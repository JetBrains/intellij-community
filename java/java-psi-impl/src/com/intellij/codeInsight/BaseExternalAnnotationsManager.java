// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight;

import com.intellij.lang.java.parser.JavaParser;
import com.intellij.lang.java.parser.JavaParserUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.LowMemoryWatcher;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.*;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.SmartList;
import com.intellij.util.containers.CollectionFactory;
import com.intellij.util.containers.ConcurrentMostlySingularMultiMap;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MostlySingularMultiMap;
import com.intellij.util.text.CharSequenceReader;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.IOException;
import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public abstract class BaseExternalAnnotationsManager extends ExternalAnnotationsManager {
  private static final Logger LOG = Logger.getInstance(BaseExternalAnnotationsManager.class);
  private static final Key<Boolean> EXTERNAL_ANNO_MARKER = Key.create("EXTERNAL_ANNO_MARKER");
  private static final List<PsiFile> NULL_LIST = Collections.emptyList();

  protected final PsiManager myPsiManager;

  private final Map<Object, List<PsiFile>> myExternalAnnotationsCache = ContainerUtil.createConcurrentWeakKeySoftValueMap();
  private final Map<AnnotationData, AnnotationData> myAnnotationDataCache = ContainerUtil.createWeakKeyWeakValueMap(); // guarded by myAnnotationDataCache
  private final Map<PsiFile, Pair<MostlySingularMultiMap<String, AnnotationData>, Long>> myAnnotationFileToDataAndModStampCache =
    CollectionFactory.createConcurrentSoftMap();

  public BaseExternalAnnotationsManager(@NotNull PsiManager psiManager) {
    myPsiManager = psiManager;
    LowMemoryWatcher.register(this::dropCache, psiManager.getProject());
  }

  /**
   * Returns canonical string presentation of {@code listOwner}
   * used in external annotations files.
   *
   * @param listOwner API element to return external name of
   * @return external name or {@code null} if the {@code listOwner}
   * is of unknown type (neither class, method, field nor parameter)
   */
  @Nullable
  protected static String getExternalName(@NotNull PsiModifierListOwner listOwner) {
    return PsiFormatUtil.getExternalName(listOwner, false, Integer.MAX_VALUE);
  }

  protected abstract boolean hasAnyAnnotationsRoots();

  @Override
  public boolean hasAnnotationRootsForFile(@NotNull VirtualFile file) {
    return hasAnyAnnotationsRoots();
  }

  @Override
  public boolean isExternalAnnotation(@NotNull PsiAnnotation annotation) {
    return annotation.getUserData(EXTERNAL_ANNO_MARKER) != null;
  }

  @Override
  @Nullable
  public PsiAnnotation findExternalAnnotation(@NotNull final PsiModifierListOwner listOwner, @NotNull final String annotationFQN) {
    List<PsiAnnotation> result = findExternalAnnotations(listOwner, annotationFQN);
    return result.isEmpty() ? null : result.get(0);
  }

  @NotNull
  @Override
  public List<PsiAnnotation> findExternalAnnotations(@NotNull PsiModifierListOwner listOwner, @NotNull String annotationFQN) {
    List<AnnotationData> result = collectExternalAnnotations(listOwner);
    return filterAnnotations(result, annotationFQN);
  }

  @Nullable
  @Override
  public List<PsiAnnotation> findDefaultConstructorExternalAnnotations(@NotNull PsiClass aClass, @NotNull String annotationFQN) {
    if (aClass.getConstructors().length > 0) {
      return null;
    }
    List<AnnotationData> result = collectDefaultConstructorExternalAnnotations(aClass);
    return filterAnnotations(result, annotationFQN);
  }

  @NotNull
  private List<PsiAnnotation> filterAnnotations(@NotNull List<AnnotationData> result, @NotNull String annotationFQN) {
    SmartList<PsiAnnotation> annotations = new SmartList<>();
    for (AnnotationData data : result) {
      if (data.annotationClassFqName.equals(annotationFQN)) {
        PsiAnnotation annotation = data.getAnnotation(this);
        annotations.add(annotation);
      }
    }
    return annotations;
  }

  @Nullable
  @Override
  public List<PsiAnnotation> findDefaultConstructorExternalAnnotations(@NotNull PsiClass aClass) {
    if (aClass.getConstructors().length > 0) {
      return null;
    }
    List<AnnotationData> result = collectDefaultConstructorExternalAnnotations(aClass);
    return StreamEx.of(result)
      .map(data -> data.getAnnotation(this))
      .toCollection(SmartList::new);
  }

  @Override
  public boolean isExternalAnnotationWritable(@NotNull PsiModifierListOwner listOwner, @NotNull final String annotationFQN) {
    // note that this method doesn't cache it's result
    List<AnnotationData> map = doCollect(listOwner, true);
    return findByFQN(map, annotationFQN) != null;
  }

  private static AnnotationData findByFQN(@NotNull List<AnnotationData> map, @NotNull final String annotationFQN) {
    return ContainerUtil.find(map, data -> data.annotationClassFqName.equals(annotationFQN));
  }

  @Override
  public PsiAnnotation @Nullable [] findExternalAnnotations(@NotNull final PsiModifierListOwner listOwner) {
    final List<AnnotationData> result = collectExternalAnnotations(listOwner);
    return result.isEmpty() ? null : ContainerUtil.map2Array(result, PsiAnnotation.EMPTY_ARRAY,
                                                             data -> data.getAnnotation(this));
  }

  private static final List<AnnotationData> NO_DATA = new ArrayList<>(1);
  private final ConcurrentMostlySingularMultiMap<Object, AnnotationData> cache = new ConcurrentMostlySingularMultiMap<>();

  // interner for storing annotation FQN
  private final CharTableImpl charTable = new CharTableImpl();

  @NotNull
  private List<AnnotationData> collectDefaultConstructorExternalAnnotations(@NotNull PsiClass aClass) {
    //External annotations of default constructor are stored at the same annotations files as class' ones.
    List<PsiFile> annotationsFiles = findExternalAnnotationsFiles(aClass);
    if (annotationsFiles == null) return NO_DATA;

    String defCtrExternalName = getExternalName(aClass) + " " + aClass.getName() + "()";
    return collectExternalAnnotations(defCtrExternalName, () -> doCollect(defCtrExternalName, annotationsFiles, false));
  }

  @NotNull
  private List<AnnotationData> collectExternalAnnotations(@NotNull PsiModifierListOwner listOwner) {
    return collectExternalAnnotations(listOwner, () -> doCollect(listOwner, false));
  }

  @NotNull
  private List<AnnotationData> collectExternalAnnotations(@NotNull Object cacheKey,
                                                          @NotNull Supplier<? extends List<AnnotationData>> dataSupplier) {
    if (!hasAnyAnnotationsRoots()) return Collections.emptyList();
    List<AnnotationData> cached;
    while (true) {
      cached = (List<AnnotationData>)cache.get(cacheKey);
      if (cached == NO_DATA || !cached.isEmpty()) return cached;
      List<AnnotationData> computed = dataSupplier.get();
      if (cache.replace(cacheKey, cached, computed)) {
        cached = computed;
        break;
      }
    }
    return cached;
  }

  @NotNull
  private AnnotationData internAnnotationData(@NotNull AnnotationData data) {
    synchronized (myAnnotationDataCache) {
      AnnotationData interned = myAnnotationDataCache.get(data);
      if (interned == null) {
        myAnnotationDataCache.put(data, data);
        interned = data;
      }
      return interned;
    }
  }

  @NotNull
  public MostlySingularMultiMap<String, AnnotationData> getDataFromFile(@NotNull PsiFile file) {
    Pair<MostlySingularMultiMap<String, AnnotationData>, Long> cached = myAnnotationFileToDataAndModStampCache.get(file);
    long fileModificationStamp = file.getModificationStamp();
    if (cached != null && cached.getSecond() == fileModificationStamp) {
      return cached.getFirst();
    }

    MostlySingularMultiMap<String, AnnotationData> result = loadData(file.getViewProvider().getVirtualFile(), file.getViewProvider().getContents(), this);
    myAnnotationFileToDataAndModStampCache.put(file, Pair.create(result, fileModificationStamp));
    return result;
  }

  public static @NotNull MostlySingularMultiMap<String, AnnotationData> loadData(@NotNull VirtualFile virtualFile,
                                                                                 @NotNull CharSequence fileText,
                                                                                 @Nullable BaseExternalAnnotationsManager externalAnnotationsManager) {
    DataParsingSaxHandler handler = new DataParsingSaxHandler(virtualFile, externalAnnotationsManager);
    try {
      SAXParser saxParser = Holder.FACTORY.newSAXParser();
      saxParser.parse(new InputSource(new CharSequenceReader(escapeAttributes(fileText))), handler);
    }
    catch (SAXParseException e) {
      if (externalAnnotationsManager != null) {
        externalAnnotationsManager.reportXmlParseError(virtualFile, e);
      } else {
        LOG.error(virtualFile.getPath(), e);
      }
    }
    catch (IOException | ParserConfigurationException | SAXException e) {
      LOG.error(virtualFile.getPath(), e);
    }

    return handler.getResult();
  }

  protected void reportXmlParseError(@NotNull VirtualFile file, @NotNull SAXParseException exception) {
    LOG.error(file.getPath(), exception);
  }

  private interface Holder {
    SAXParserFactory FACTORY = SAXParserFactory.newInstance();
  }

  protected void duplicateError(@NotNull VirtualFile virtualFile,
                                @NotNull String externalName,
                                @NotNull String text) {
    LOG.error(text + "; for signature: '" + externalName + "' in the " + virtualFile);
  }

  @NotNull
  private String intern(@NotNull String annotationFQN) {
    synchronized (charTable) {
      return charTable.doIntern(annotationFQN).toString();
    }
  }

  @NotNull
  private List<AnnotationData> doCollect(@NotNull PsiModifierListOwner listOwner, boolean onlyWritable) {
    List<PsiFile> files = findExternalAnnotationsFiles(listOwner);
    if (files == null) return NO_DATA;

    String externalName = getExternalName(listOwner);
    if (externalName == null) return NO_DATA;

    return doCollect(externalName, files, onlyWritable);
  }

  @NotNull
  private List<AnnotationData> doCollect(@NotNull String externalName, @NotNull List<? extends PsiFile> annotationsFiles, boolean onlyWritable) {
    SmartList<AnnotationData> result = new SmartList<>();
    for (PsiFile file : annotationsFiles) {
      if (!file.isValid()) continue;
      if (onlyWritable && !file.isWritable()) continue;

      MostlySingularMultiMap<String, AnnotationData> fileData = getDataFromFile(file);
      ContainerUtil.addAll(result, fileData.get(externalName));
    }
    if (result.isEmpty()) return NO_DATA;

    result.trimToSize();
    return result;
  }

  @Override
  @Nullable
  public List<PsiFile> findExternalAnnotationsFiles(@NotNull PsiModifierListOwner listOwner) {
    if (listOwner instanceof PsiPackage) {
      String packageName = ((PsiPackage)listOwner).getQualifiedName();
      Function<PsiPackage, List<VirtualFile>> roots = pack -> Arrays.stream(pack.getDirectories())
        .flatMap(dir -> getExternalAnnotationsRoots(dir.getVirtualFile()).stream())
        .distinct()
        .collect(Collectors.toList());
      return findExternalAnnotationsFiles(packageName, (PsiPackage)listOwner, roots);
    }
    final PsiFile containingFile = PsiUtil.preferCompiledElement(listOwner).getContainingFile();
    if (!(containingFile instanceof PsiClassOwner)) return null;

    final VirtualFile virtualFile = containingFile.getVirtualFile();
    if (virtualFile == null || !virtualFile.isValid()) return null;

    return findExternalAnnotationsFiles(((PsiClassOwner)containingFile).getPackageName(), virtualFile, f -> getExternalAnnotationsRoots(f));
  }

  private <T> List<PsiFile> findExternalAnnotationsFiles(String packageName, @NotNull T key, @NotNull Function<? super T, ? extends List<VirtualFile>> rootGetter) {
    List<PsiFile> files = myExternalAnnotationsCache.get(key);
    if (files == NULL_LIST) return null;

    if (files != null) {
      boolean allValid = true;
      for (PsiFile file : files) {
        if (!file.isValid()) {
          allValid = false;
          break;
        }
      }
      if (allValid) {
        return files;
      }
    }

    Set<PsiFile> possibleAnnotationXmls = new HashSet<>();

    String relativePath = packageName.replace('.', '/') + '/' + ANNOTATIONS_XML;
    for (VirtualFile root : rootGetter.apply(key)) {
      VirtualFile ext = root.findFileByRelativePath(relativePath);
      if (ext != null && ext.isValid()) {
        PsiFile psiFile = myPsiManager.findFile(ext);
        if (psiFile != null) {
          possibleAnnotationXmls.add(psiFile);
        }
      }
    }

    if (possibleAnnotationXmls.isEmpty()) {
      myExternalAnnotationsCache.put(key, NULL_LIST);
      return null;
    }

    List<PsiFile> result = new ArrayList<>(possibleAnnotationXmls);
    // writable go first
    result.sort((f1, f2) -> {
      boolean w1 = f1.isWritable();
      boolean w2 = f2.isWritable();
      return w1 == w2 ? 0 : w1 ? -1 : 1;
    });
    myExternalAnnotationsCache.put(key, result);
    return result;
  }

  @NotNull
  protected abstract List<VirtualFile> getExternalAnnotationsRoots(@NotNull VirtualFile libraryFile);

  protected void dropCache() {
    myExternalAnnotationsCache.clear();
    myAnnotationFileToDataAndModStampCache.clear();
    cache.clear();
  }

  // This method is used for legacy reasons.
  // Old external annotations sometimes are bad XML: they have "<" and ">" characters in attributes values. To prevent SAX parser from
  // failing, we escape attributes values.
  @NotNull
  private static CharSequence escapeAttributes(@NotNull CharSequence invalidXml) {
    if (!hasInvalidAttribute(invalidXml)) {
      return invalidXml;
    }
    // We assume that XML has single- and double-quote characters only for attribute values, therefore we don't any complex parsing,
    // just have binary inAttribute state
    StringBuilder buf = new StringBuilder(invalidXml.length());
    boolean inAttribute = false;
    for (int i = 0; i < invalidXml.length(); i++) {
      char c = invalidXml.charAt(i);
      if (inAttribute && c == '<') {
        buf.append("&lt;");
      }
      else if (inAttribute && c == '>') {
        buf.append("&gt;");
      }
      else if (c == '\"' || c == '\'') {
        buf.append('\"');
        inAttribute = !inAttribute;
      }
      else {
        buf.append(c);
      }
    }
    return buf;
  }

  private static boolean hasInvalidAttribute(@NotNull CharSequence invalidXml) {
    boolean inAttribute = false;
    for (int i = 0; i < invalidXml.length(); i++) {
      char c = invalidXml.charAt(i);
      if (inAttribute && c == '<') {
        return true;
      }
      else if (inAttribute && c == '>') {
        return true;
      }
      else if (c == '\"' || c == '\'') {
        inAttribute = !inAttribute;
      }
    }
    return false;
  }

  @Override
  public void annotateExternally(@NotNull PsiModifierListOwner listOwner,
                                 @NotNull String annotationFQName,
                                 @NotNull PsiFile fromFile,
                                 PsiNameValuePair @Nullable [] value) throws CanceledConfigurationException {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean deannotate(@NotNull PsiModifierListOwner listOwner, @NotNull String annotationFQN) {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean editExternalAnnotation(@NotNull PsiModifierListOwner listOwner,
                                        @NotNull String annotationFQN,
                                        PsiNameValuePair @Nullable [] value) {
    throw new UnsupportedOperationException();
  }

  @NotNull
  @Override
  public AnnotationPlace chooseAnnotationsPlaceNoUi(@NotNull PsiElement element) {
    throw new UnsupportedOperationException();
  }

  @NotNull
  @Override
  public AnnotationPlace chooseAnnotationsPlace(@NotNull PsiElement element) {
    throw new UnsupportedOperationException();
  }

  protected void registerExternalAnnotations(@NotNull Object key, @NotNull PsiFile annotationsFile) {
    myExternalAnnotationsCache.compute(key, (k, v) -> {
      if (v == null || v == NULL_LIST) {
        return new SmartList<>(annotationsFile);
      }

      v.add(annotationsFile);
      return v;
    });
  }

  public static final class AnnotationData {
    private final String annotationClassFqName;
    private final String annotationParameters;

    private volatile PsiAnnotation myAnnotation;

    private AnnotationData(@NotNull String fqn, @NotNull String parameters) {
      annotationClassFqName = fqn;
      annotationParameters = parameters;
    }

    @NotNull
    public PsiAnnotation getAnnotation(@NotNull BaseExternalAnnotationsManager context) {
      PsiAnnotation a = myAnnotation;
      if (a == null) {
        String text = "@" + annotationClassFqName + (annotationParameters.isEmpty() ? "" : "(" + annotationParameters + ")");
        myAnnotation = a = context.createAnnotationFromText(text);
      }
      return a;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      AnnotationData data = (AnnotationData)o;

      return annotationClassFqName.equals(data.annotationClassFqName) && annotationParameters.equals(data.annotationParameters);
    }

    @Override
    public int hashCode() {
      int result = annotationClassFqName.hashCode();
      result = 31 * result + annotationParameters.hashCode();
      return result;
    }

    @Override
    public String toString() {
      return annotationClassFqName + "(" + annotationParameters + ")";
    }
  }

  private static PsiAnnotation markAsExternalAnnotation(@NotNull PsiAnnotation annotation) {
    annotation.putUserData(EXTERNAL_ANNO_MARKER, Boolean.TRUE);
    ((LightVirtualFile)annotation.getContainingFile().getViewProvider().getVirtualFile()).setWritable(false);
    return annotation;
  }

  @NotNull
  private PsiAnnotation createAnnotationFromText(@NotNull final String text) throws IncorrectOperationException {
    final JavaParserUtil.ParserWrapper ANNOTATION = JavaParser.INSTANCE.getDeclarationParser()::parseAnnotation;
    // synchronize during interning in charTable
    synchronized (charTable) {
      DummyHolder holder = DummyHolderFactory.createHolder(myPsiManager, new JavaDummyElement(text, ANNOTATION, LanguageLevel.HIGHEST), null, charTable);
      PsiElement element = SourceTreeToPsiMap.treeElementToPsi(holder.getTreeElement().getFirstChildNode());
      if (!(element instanceof PsiAnnotation)) {
        throw new IncorrectOperationException("Incorrect annotation \"" + text + "\".");
      }
      return markAsExternalAnnotation((PsiAnnotation)element);
    }
  }

  private static final class DataParsingSaxHandler extends DefaultHandler {
    private final MostlySingularMultiMap<String, AnnotationData> myData = new MostlySingularMultiMap<>();
    private final @NotNull VirtualFile myFile;
    private final BaseExternalAnnotationsManager myExternalAnnotationsManager;

    private String myExternalName;
    private String myAnnotationFqn;
    private StringBuilder myArguments;

    private DataParsingSaxHandler(@NotNull VirtualFile file, @Nullable BaseExternalAnnotationsManager manager) {
      myFile = file;
      myExternalAnnotationsManager = manager;
    }

    @Override
    public void startElement(String uri, String localName, String qName, Attributes attributes) {
      if ("item".equals(qName)) {
        myExternalName = attributes.getValue("name");
      }
      else if ("annotation".equals(qName)) {
        myAnnotationFqn = attributes.getValue("name");
        myArguments = new StringBuilder();
      }
      else if ("val".equals(qName)) {
        if (myArguments.length() != 0) {
          myArguments.append(",");
        }
        String name = attributes.getValue("name");
        if (name != null) {
          myArguments.append(name);
          myArguments.append("=");
        }
        myArguments.append(attributes.getValue("val"));
      }
    }

    @Override
    public void endElement(String uri, String localName, String qName) {
      if ("item".equals(qName)) {
        myExternalName = null;
      }
      else if ("annotation".equals(qName) && myExternalName != null && myAnnotationFqn != null) {
        for (AnnotationData existingData : myData.get(myExternalName)) {
          if (existingData.annotationClassFqName.equals(myAnnotationFqn) && myExternalAnnotationsManager != null) {
            myExternalAnnotationsManager.duplicateError(myFile, myExternalName, "Duplicate annotation '" + myAnnotationFqn + "'");
          }
        }

        String argumentsString = myArguments.length() == 0 ? "" : myExternalAnnotationsManager == null ? myArguments.toString() : myExternalAnnotationsManager.intern(myArguments.toString());
        AnnotationData data = new AnnotationData(myAnnotationFqn, argumentsString);
        myData.add(myExternalName, myExternalAnnotationsManager == null ? data : myExternalAnnotationsManager.internAnnotationData(data));

        myAnnotationFqn = null;
        myArguments = null;
      }
    }

    @NotNull
    public MostlySingularMultiMap<String, AnnotationData> getResult() {
      if (myData.isEmpty()) {
        return MostlySingularMultiMap.emptyMap();
      }
      myData.compact();
      return myData;
    }
  }
}
