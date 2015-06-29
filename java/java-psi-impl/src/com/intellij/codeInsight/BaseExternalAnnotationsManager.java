/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.codeInsight;

import com.intellij.lang.PsiBuilder;
import com.intellij.lang.java.parser.JavaParser;
import com.intellij.lang.java.parser.JavaParserUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.LowMemoryWatcher;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.*;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.util.Function;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ConcurrentMostlySingularMultiMap;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MostlySingularMultiMap;
import com.intellij.util.containers.WeakKeyWeakValueHashMap;
import com.intellij.util.text.CharSequenceReader;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentMap;

public abstract class BaseExternalAnnotationsManager extends ExternalAnnotationsManager {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInsight.BaseExternalAnnotationsManager");
  private static final Key<Boolean> EXTERNAL_ANNO_MARKER = Key.create("EXTERNAL_ANNO_MARKER");
  @SuppressWarnings("MismatchedQueryAndUpdateOfCollection")
  @NotNull private static final List<PsiFile> NULL_LIST = new ArrayList<PsiFile>(0);
  @NotNull
  private final ConcurrentMap<VirtualFile, List<PsiFile>> myExternalAnnotations = ContainerUtil.createConcurrentSoftValueMap();
  protected final PsiManager myPsiManager;

  public BaseExternalAnnotationsManager(final PsiManager psiManager) {
    myPsiManager = psiManager;
    LowMemoryWatcher.register(new Runnable() {
      @Override
      public void run() {
        dropCache();
      }
    }, psiManager.getProject());
  }

  @Nullable
  protected static String getExternalName(@NotNull PsiModifierListOwner listOwner, boolean showParamName) {
    return PsiFormatUtil.getExternalName(listOwner, showParamName, Integer.MAX_VALUE);
  }

  @NotNull
  static PsiModifierListOwner preferCompiledElement(@NotNull PsiModifierListOwner element) {
    PsiElement original = element.getOriginalElement();
    return original instanceof PsiModifierListOwner ? (PsiModifierListOwner)original : element;
  }

  protected abstract boolean hasAnyAnnotationsRoots();

  @Override
  public boolean isExternalAnnotation(@NotNull PsiAnnotation annotation) {
    return annotation.getUserData(EXTERNAL_ANNO_MARKER) != null;
  }

  @Override
  @Nullable
  public PsiAnnotation findExternalAnnotation(@NotNull final PsiModifierListOwner listOwner, @NotNull final String annotationFQN) {
    List<AnnotationData> list = collectExternalAnnotations(listOwner);
    AnnotationData data = findByFQN(list, annotationFQN);
    return data == null ? null : data.getAnnotation(this);
  }

  @Override
  public boolean isExternalAnnotationWritable(@NotNull PsiModifierListOwner listOwner, @NotNull final String annotationFQN) {
    // note that this method doesn't cache it's result
    List<AnnotationData> map = doCollect(listOwner, true);
    return findByFQN(map, annotationFQN) != null;
  }

  private static AnnotationData findByFQN(@NotNull List<AnnotationData> map, @NotNull final String annotationFQN) {
    return ContainerUtil.find(map, new Condition<AnnotationData>() {
      @Override
      public boolean value(AnnotationData data) {
        return data.annotationClassFqName.equals(annotationFQN);
      }
    });
  }

  @Override
  @Nullable
  public PsiAnnotation[] findExternalAnnotations(@NotNull final PsiModifierListOwner listOwner) {
    final List<AnnotationData> result = collectExternalAnnotations(listOwner);
    return result.isEmpty() ? null : ContainerUtil.map2Array(result, PsiAnnotation.EMPTY_ARRAY, new Function<AnnotationData, PsiAnnotation>() {
      @Override
      public PsiAnnotation fun(AnnotationData data) {
        return data.getAnnotation(BaseExternalAnnotationsManager.this);
      }
    });
  }

  private static final List<AnnotationData> NO_DATA = new ArrayList<AnnotationData>(1);
  private final ConcurrentMostlySingularMultiMap<PsiModifierListOwner, AnnotationData> cache = new ConcurrentMostlySingularMultiMap<PsiModifierListOwner, AnnotationData>();

  // interner for storing annotation FQN
  private final CharTableImpl charTable = new CharTableImpl();

  @NotNull
  private List<AnnotationData> collectExternalAnnotations(@NotNull PsiModifierListOwner listOwner) {
    if (!hasAnyAnnotationsRoots()) return Collections.emptyList();

    List<AnnotationData> cached;
    while (true) {
      cached = (List<AnnotationData>)cache.get(listOwner);
      if (cached == NO_DATA || !cached.isEmpty()) return cached;
      List<AnnotationData> computed = doCollect(listOwner, false);
      if (cache.replace(listOwner, cached, computed)) {
        cached = computed;
        break;
      }
    }
    return cached;
  }

  private final Map<AnnotationData, AnnotationData> annotationDataCache = new WeakKeyWeakValueHashMap<AnnotationData, AnnotationData>();
  @NotNull
  private AnnotationData internAnnotationData(@NotNull AnnotationData data) {
    synchronized (annotationDataCache) {
      AnnotationData interned = annotationDataCache.get(data);
      if (interned == null) {
        annotationDataCache.put(data, data);
        interned = data;
      }
      return interned;
    }
  }


  private final ConcurrentMap<PsiFile, Pair<MostlySingularMultiMap<String, AnnotationData>, Long>> annotationFileToDataAndModStamp = ContainerUtil.createConcurrentSoftMap();

  @NotNull
  private MostlySingularMultiMap<String, AnnotationData> getDataFromFile(@NotNull final PsiFile file) {
    Pair<MostlySingularMultiMap<String, AnnotationData>, Long> cached = annotationFileToDataAndModStamp.get(file);
    final long fileModificationStamp = file.getModificationStamp();
    if (cached != null && cached.getSecond() == fileModificationStamp) {
      return cached.getFirst();
    }
    DataParsingSaxHandler handler = new DataParsingSaxHandler(file);
    try {
      SAXParser saxParser = SAXParserFactory.newInstance().newSAXParser();
      saxParser.parse(new InputSource(new CharSequenceReader(escapeAttributes(file.getViewProvider().getContents()))), handler);
    }
    catch (IOException e) {
      LOG.error(e);
    }
    catch (ParserConfigurationException e) {
      LOG.error(e);
    }
    catch (SAXException e) {
      LOG.error(e);
    }

    Pair<MostlySingularMultiMap<String, AnnotationData>, Long> pair = Pair.create(handler.getResult(), file.getModificationStamp());
    annotationFileToDataAndModStamp.put(file, pair);

    return pair.first;
  }

  protected void duplicateError(@NotNull PsiFile file, @NotNull String externalName, @NotNull String text) {
    LOG.error(text + "; for signature: '" + externalName + "' in the " + file.getVirtualFile());
  }

  @NotNull
  private String intern(@NotNull String annotationFQN) {
    synchronized (charTable) {
      return charTable.doIntern(annotationFQN).toString();
    }
  }

  @NotNull
  private List<AnnotationData> doCollect(@NotNull PsiModifierListOwner listOwner, boolean onlyWritable) {
    final List<PsiFile> files = findExternalAnnotationsFiles(listOwner);
    if (files == null) {
      return NO_DATA;
    }
    SmartList<AnnotationData> result = new SmartList<AnnotationData>();
    String externalName = getExternalName(listOwner, false);
    if (externalName == null) return NO_DATA;

    for (PsiFile file : files) {
      if (!file.isValid()) continue;
      if (onlyWritable && !file.isWritable()) continue;

      MostlySingularMultiMap<String, AnnotationData> fileData = getDataFromFile(file);

      ContainerUtil.addAll(result, fileData.get(externalName));
    }
    if (result.isEmpty()) {
      return NO_DATA;
    }
    result.trimToSize();
    return result;
  }

  @Override
  @Nullable
  public List<PsiFile> findExternalAnnotationsFiles(@NotNull PsiModifierListOwner _listOwner) {
    final PsiModifierListOwner listOwner = preferCompiledElement(_listOwner);
    final PsiFile containingFile = listOwner.getContainingFile();
    if (!(containingFile instanceof PsiJavaFile)) {
      return null;
    }
    final PsiJavaFile javaFile = (PsiJavaFile)containingFile;
    final String packageName = javaFile.getPackageName();
    final VirtualFile virtualFile = containingFile.getVirtualFile();
    if (virtualFile == null) return null;
    
    final List<PsiFile> files = myExternalAnnotations.get(virtualFile);
    if (files == NULL_LIST) return null;
    if (files != null) {
      boolean allValid = true;
      for (PsiFile file : files) {
        allValid &= file.isValid();
      }
      if (allValid) {
        return files;
      }
    }

    Set<PsiFile> possibleAnnotationsXmls = new THashSet<PsiFile>();
    for (VirtualFile root : getExternalAnnotationsRoots(virtualFile)) {
      final VirtualFile ext = root.findFileByRelativePath(packageName.replace('.', '/') + "/" + ANNOTATIONS_XML);
      if (ext == null) continue;
      final PsiFile psiFile = myPsiManager.findFile(ext);
      if (psiFile == null) continue;
      possibleAnnotationsXmls.add(psiFile);
    }
    List<PsiFile> result;
    if (possibleAnnotationsXmls.isEmpty()) {
      myExternalAnnotations.put(virtualFile, NULL_LIST);
      result = null;
    }
    else {
      result = new SmartList<PsiFile>(possibleAnnotationsXmls);
      // sorting by writability: writable go first
      Collections.sort(result, new Comparator<PsiFile>() {
        @Override
        public int compare(PsiFile f1, PsiFile f2) {
          boolean w1 = f1.isWritable();
          boolean w2 = f2.isWritable();
          if (w1 == w2) {
            return 0;
          }
          return w1 ? -1 : 1;
        }
      });

      myExternalAnnotations.put(virtualFile, result);
    }
    return result;
  }

  @NotNull
  protected abstract List<VirtualFile> getExternalAnnotationsRoots(@NotNull VirtualFile libraryFile);

  protected void dropCache() {
    myExternalAnnotations.clear();
    annotationFileToDataAndModStamp.clear();
    cache.clear();
  }

  // This method is used for legacy reasons.
  // Old external annotations sometimes are bad XML: they have "<" and ">" characters in attributes values. To prevent SAX parser from
  // failing, we escape attributes values.
  @NotNull
  private static CharSequence escapeAttributes(@NotNull CharSequence invalidXml) {
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

  @Override
  public void annotateExternally(@NotNull PsiModifierListOwner listOwner,
                                 @NotNull String annotationFQName,
                                 @NotNull PsiFile fromFile,
                                 @Nullable PsiNameValuePair[] value) {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean deannotate(@NotNull PsiModifierListOwner listOwner, @NotNull String annotationFQN) {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean editExternalAnnotation(@NotNull PsiModifierListOwner listOwner,
                                        @NotNull String annotationFQN,
                                        @Nullable PsiNameValuePair[] value) {
    throw new UnsupportedOperationException();
  }

  @Override
  public AnnotationPlace chooseAnnotationsPlace(@NotNull PsiElement element) {
    throw new UnsupportedOperationException();
  }

  protected void cacheExternalAnnotations(@NotNull String packageName, @NotNull PsiFile fromFile, @NotNull List<PsiFile> annotationFiles) {
    VirtualFile virtualFile = fromFile.getVirtualFile();
    if (virtualFile != null) {
      myExternalAnnotations.put(virtualFile, annotationFiles);
    }
  }

  private static class AnnotationData {
    @NotNull private final String annotationClassFqName;
    @NotNull private final String annotationParameters;
    private volatile PsiAnnotation annotation;

    private AnnotationData(@NotNull String annotationClassFqName, @NotNull String annotationParameters) {
      this.annotationClassFqName = annotationClassFqName;
      this.annotationParameters = annotationParameters;
    }

    @NotNull
    private PsiAnnotation getAnnotation(@NotNull BaseExternalAnnotationsManager context) {
      PsiAnnotation a = annotation;
      if (a == null) {
        a = context.createAnnotationFromText("@" + annotationClassFqName + (annotationParameters.isEmpty() ? "" : "("+annotationParameters+")"));
        annotation = markAsExternalAnnotation(a);
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
      return annotationClassFqName + "("+annotationParameters+")";
    }
  }

  private static PsiAnnotation markAsExternalAnnotation(@NotNull PsiAnnotation annotation) {
    annotation.putUserData(EXTERNAL_ANNO_MARKER, Boolean.TRUE);
    ((LightVirtualFile)annotation.getContainingFile().getViewProvider().getVirtualFile()).markReadOnly();
    return annotation;
  }

  @NotNull
  private PsiAnnotation createAnnotationFromText(@NotNull final String text) throws IncorrectOperationException {
    // synchronize during interning in charTable
    synchronized (charTable) {
      final DummyHolder holder = DummyHolderFactory.createHolder(myPsiManager, new JavaDummyElement(text, ANNOTATION, LanguageLevel.HIGHEST), null, charTable);
      final PsiElement element = SourceTreeToPsiMap.treeElementToPsi(holder.getTreeElement().getFirstChildNode());
      if (!(element instanceof PsiAnnotation)) {
        throw new IncorrectOperationException("Incorrect annotation \"" + text + "\".");
      }
      return markAsExternalAnnotation((PsiAnnotation)element);
    }
  }
  private static final JavaParserUtil.ParserWrapper ANNOTATION = new JavaParserUtil.ParserWrapper() {
    @Override
    public void parse(final PsiBuilder builder) {
      JavaParser.INSTANCE.getDeclarationParser().parseAnnotation(builder);
    }
  };

  private class DataParsingSaxHandler extends DefaultHandler {
    private final MostlySingularMultiMap<String, AnnotationData> data = new MostlySingularMultiMap<String, AnnotationData>();

    private final PsiFile file;

    private String externalName = null;
    private String annotationFQN = null;
    private StringBuilder arguments = null;

    private DataParsingSaxHandler(PsiFile file) {
      this.file = file;
    }

    @Override
    public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
      if ("item".equals(qName)) {
        externalName = attributes.getValue("name");
      } else if ("annotation".equals(qName)) {
        annotationFQN = attributes.getValue("name");
        arguments = new StringBuilder();
      } else if ("val".equals(qName)) {
        if (arguments.length() != 0) {
          arguments.append(",");
        }
        String name = attributes.getValue("name");
        if (name != null) {
          arguments.append(name);
          arguments.append("=");
        }
        arguments.append(attributes.getValue("val"));
      }
    }

    @Override
    public void endElement(String uri, String localName, String qName) throws SAXException {
      if ("item".equals(qName)) {
        externalName = null;
      } else if ("annotation".equals(qName)) {
        if (externalName != null && annotationFQN != null) {
          String argumentsString = arguments.length() == 0 ? "" : intern(arguments.toString());
          for (AnnotationData existingData : data.get(externalName)) {
            if (existingData.annotationClassFqName.equals(annotationFQN)) {
              duplicateError(file, externalName, "Duplicate annotation '" + annotationFQN + "' ");
            }
          }
          AnnotationData annData = internAnnotationData(new AnnotationData(annotationFQN, argumentsString));
          data.add(externalName, annData);
          annotationFQN = null;
          arguments = null;
        }
      }
    }

    public MostlySingularMultiMap<String, AnnotationData> getResult() {
      if (data.isEmpty()) {
        return MostlySingularMultiMap.emptyMap();
      }
      data.compact();
      return data;
    }
  }
}
