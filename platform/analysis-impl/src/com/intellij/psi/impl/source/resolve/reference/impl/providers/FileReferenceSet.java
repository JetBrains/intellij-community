// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.psi.impl.source.resolve.reference.impl.providers;

import com.intellij.analysis.AnalysisBundle;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.model.ModelBranch;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Conditions;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.indexing.FileBasedIndex;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;

import static com.intellij.psi.impl.source.resolve.reference.impl.providers.FileTargetContext.toTargetContexts;
import static java.util.Collections.*;

/**
 * @author Maxim.Mossienko
 */
public class FileReferenceSet {
  private static final Logger LOG = Logger.getInstance(FileReferenceSet.class);

  private static final FileType[] EMPTY_FILE_TYPES = {};

  public static final CustomizableReferenceProvider.CustomizationKey<Function<PsiFile, Collection<PsiFileSystemItem>>>
    DEFAULT_PATH_EVALUATOR_OPTION =
    new CustomizableReferenceProvider.CustomizationKey<>(
      AnalysisBundle.message("default.path.evaluator.option"));
  public static final Function<PsiFile, Collection<PsiFileSystemItem>> ABSOLUTE_TOP_LEVEL = new AbsoluteTopLevelEvaluator();

  public static final Condition<PsiFileSystemItem> FILE_FILTER = item -> item instanceof PsiFile;

  public static final Condition<PsiFileSystemItem> DIRECTORY_FILTER = item -> item instanceof PsiDirectory;

  protected FileReference[] myReferences;
  private PsiElement myElement;
  private final int myStartInElement;
  private final boolean myCaseSensitive;
  private final String myPathStringNonTrimmed;
  private final String myPathString;

  private volatile Collection<PsiFileSystemItem> myDefaultContexts;

  private final boolean myEndingSlashNotAllowed;
  private boolean myEmptyPathAllowed;
  @Nullable private Map<CustomizableReferenceProvider.CustomizationKey, Object> myOptions;
  private FileType @Nullable [] mySuitableFileTypes;

  public FileReferenceSet(@NotNull String str,
                          @NotNull PsiElement element,
                          int startInElement,
                          PsiReferenceProvider provider,
                          boolean caseSensitive,
                          boolean endingSlashNotAllowed,
                          FileType @Nullable [] suitableFileTypes) {
    this(str, element, startInElement, provider, caseSensitive, endingSlashNotAllowed, suitableFileTypes, true);
  }

  public FileReferenceSet(@NotNull String str,
                          @NotNull PsiElement element,
                          int startInElement,
                          PsiReferenceProvider provider,
                          boolean caseSensitive,
                          boolean endingSlashNotAllowed,
                          FileType @Nullable [] suitableFileTypes,
                          boolean init) {
    myElement = element;
    myStartInElement = startInElement;
    myCaseSensitive = caseSensitive;
    myPathStringNonTrimmed = str;
    myPathString = str.trim();
    myEndingSlashNotAllowed = endingSlashNotAllowed;
    myEmptyPathAllowed = !endingSlashNotAllowed;
    myOptions = provider instanceof CustomizableReferenceProvider ? ((CustomizableReferenceProvider)provider).getOptions() : null;
    mySuitableFileTypes = suitableFileTypes;

    if (init) {
      reparse();
    }
  }

  protected String getNewAbsolutePath(PsiFileSystemItem root, String relativePath) {
    return absoluteUrlNeedsStartSlash() ? "/" + relativePath : relativePath;
  }

  public String getSeparatorString() {
    return "/";
  }

  protected int findSeparatorLength(@NotNull CharSequence sequence, int atOffset) {
    return StringUtil.startsWith(sequence, atOffset, getSeparatorString()) ?
           getSeparatorString().length() : 0;
  }

  protected int findSeparatorOffset(@NotNull CharSequence sequence, int startingFrom) {
    return StringUtil.indexOf(sequence, getSeparatorString(), startingFrom);
  }

  /**
   * This should be removed.
   * @deprecated use {@link FileReference#getContexts()} instead.
   */
  @Deprecated(forRemoval = true)
  protected Collection<PsiFileSystemItem> getExtraContexts() {
    return emptyList();
  }

  public static FileReferenceSet createSet(@NotNull PsiElement element,
                                           final boolean soft,
                                           boolean endingSlashNotAllowed,
                                           final boolean urlEncoded) {

    final TextRange range = ElementManipulators.getValueTextRange(element);
    int offset = range.getStartOffset();
    String text = range.substring(element.getText());
    for (final FileReferenceHelper helper : FileReferenceHelperRegistrar.getHelpers()) {
      text = helper.trimUrl(text);
    }

    return new FileReferenceSet(text, element, offset, null, true, endingSlashNotAllowed) {
      @Override
      protected boolean isUrlEncoded() {
        return urlEncoded;
      }

      @Override
      protected boolean isSoft() {
        return soft;
      }
    };
  }


  public FileReferenceSet(String str,
                          @NotNull PsiElement element,
                          int startInElement,
                          @Nullable PsiReferenceProvider provider,
                          final boolean isCaseSensitive) {
    this(str, element, startInElement, provider, isCaseSensitive, true);
  }


  public FileReferenceSet(@NotNull String str,
                          @NotNull PsiElement element,
                          int startInElement,
                          PsiReferenceProvider provider,
                          final boolean isCaseSensitive,
                          boolean endingSlashNotAllowed) {
    this(str, element, startInElement, provider, isCaseSensitive, endingSlashNotAllowed, null);
  }

  public FileReferenceSet(@NotNull final PsiElement element) {
    myElement = element;
    TextRange range = ElementManipulators.getValueTextRange(element);
    myStartInElement = range.getStartOffset();
    myPathStringNonTrimmed = range.substring(element.getText());
    myPathString = myPathStringNonTrimmed.trim();
    myEndingSlashNotAllowed = true;
    myCaseSensitive = false;

    reparse();
  }

  @NotNull
  public PsiElement getElement() {
    return myElement;
  }

  void setElement(@NotNull PsiElement element) {
    myElement = element;
  }

  public boolean isCaseSensitive() {
    return myCaseSensitive;
  }

  public boolean isEndingSlashNotAllowed() {
    return myEndingSlashNotAllowed;
  }

  public int getStartInElement() {
    return myStartInElement;
  }

  @Nullable
  public FileReference createFileReference(final TextRange range, final int index, final String text) {
    return new FileReference(this, range, index, text);
  }

  protected void reparse() {
    List<FileReference> referencesList = reparse(myPathStringNonTrimmed, myStartInElement);
    myReferences = referencesList.toArray(FileReference.EMPTY);
  }

  protected List<FileReference> reparse(String str, int startInElement) {
    int wsHead = 0;
    int wsTail = 0;

    LiteralTextEscaper<? extends PsiLanguageInjectionHost> escaper;
    TextRange valueRange;
    CharSequence decoded;
    // todo replace @param str with honest @param rangeInElement; and drop the following startsWith(..)
    if (myElement instanceof PsiLanguageInjectionHost && !StringUtil.startsWith(myElement.getText(), startInElement, str)) {
      escaper = ((PsiLanguageInjectionHost)myElement).createLiteralTextEscaper();
      valueRange = ElementManipulators.getValueTextRange(myElement);
      StringBuilder sb = new StringBuilder();
      escaper.decode(valueRange, sb);
      decoded = sb;
      wsHead += Math.max(0, startInElement - valueRange.getStartOffset());
    }
    else {
      escaper = null;
      decoded = str;
      valueRange = TextRange.from(startInElement, decoded.length());
    }
    List<FileReference> referencesList = new ArrayList<>();

    for (int i = wsHead; i < decoded.length() && Character.isWhitespace(decoded.charAt(i)); i++) {
      wsHead++;     // skip head white spaces
    }
    for (int i = decoded.length() - 1; i >= 0 && Character.isWhitespace(decoded.charAt(i)); i--) {
      wsTail++;     // skip tail white spaces
    }

    int index = 0;
    int curSep = findSeparatorOffset(decoded, wsHead);
    int sepLen = curSep >= wsHead ? findSeparatorLength(decoded, curSep) : 0;

    if (curSep >= 0 && decoded.length() == wsHead + sepLen + wsTail) {
      // add extra reference for the only & leading "/"
      TextRange r = TextRange.create(startInElement, offset(curSep + Math.max(0, sepLen - 1), escaper, valueRange) + 1);
      FileReference reference = createFileReference(r, index++, decoded.subSequence(curSep, curSep + sepLen).toString());
      if (reference != null) {
        referencesList.add(reference);
      }
    }
    curSep = curSep == wsHead ? curSep + sepLen : wsHead; // reset offsets & start again for simplicity
    sepLen = 0;
    while (curSep >= 0) {
      int nextSep = findSeparatorOffset(decoded, curSep + sepLen);
      int start = curSep + sepLen;
      int endTrimmed = nextSep > 0 ? nextSep : Math.max(start, decoded.length() - wsTail);
      // todo move ${placeholder} support (the str usage below) to a reference implementation
      // todo reference-set should be bound to exact range & text in a file, consider: ${slash}path${slash}file&amp;.txt
      String refText = index == 0 && nextSep < 0 && !StringUtil.contains(decoded, str) ? str :
                                decoded.subSequence(start, endTrimmed).toString();
      int refStart = offset(start, escaper, valueRange);
      int refEnd = offset(endTrimmed, escaper, valueRange);
      if (!(refStart <= refEnd && refStart >= 0)) {
        LOG.error("Invalid range: (" + (refText + ", " + refEnd) + "), escaper=" + escaper + "\n" +
                  "text=" + refText + ", start=" + startInElement);
      }
      FileReference reference = createFileReference(new TextRange(refStart, refEnd), index++, refText);
      if (reference != null) {
        referencesList.add(reference);
      }
      curSep = nextSep;
      sepLen = curSep > 0 ? findSeparatorLength(decoded, curSep) : 0;
    }

    return referencesList;
  }

  private static int offset(int offset, LiteralTextEscaper<? extends PsiLanguageInjectionHost> escaper, TextRange valueRange) {
    return escaper == null ? offset + valueRange.getStartOffset() : escaper.getOffsetInHost(offset, valueRange);
  }

  public FileReference getReference(int index) {
    return myReferences[index];
  }

  public FileReference @NotNull [] getAllReferences() {
    return myReferences;
  }

  protected boolean isSoft() {
    return false;
  }

  protected boolean isUrlEncoded() {
    return false;
  }

  @NotNull
  public Collection<PsiFileSystemItem> getDefaultContexts() {
    Collection<PsiFileSystemItem> result = myDefaultContexts;
    if (result == null) {
      result = computeDefaultContexts();
      ModelBranch branch = ModelBranch.getPsiBranch(getElement());
      if (branch != null) {
        result = result.stream()
          .map(item -> ModelBranch.getPsiBranch(item) == branch ? item : branch.obtainPsiCopy(item))
          .collect(Collectors.toCollection(LinkedHashSet::new));
      }
      myDefaultContexts = result;
    }
    return result;
  }

  @NotNull
  public Collection<PsiFileSystemItem> computeDefaultContexts() {
    final PsiFile file = getContainingFile();
    if (file == null) return emptyList();

    Collection<PsiFileSystemItem> contexts = getCustomizationContexts(file);
    if (contexts != null) {
      return contexts;
    }

    if (isAbsolutePathReference()) {
      return getAbsoluteTopLevelDirLocations(file);
    }

    return getContextByFile(file);
  }

  @Nullable
  protected PsiFile getContainingFile() {
    PsiFile cf = myElement.getContainingFile();
    PsiFile file = InjectedLanguageManager.getInstance(cf.getProject()).getTopLevelFile(cf);
    if (file != null) return file.getOriginalFile();
    LOG.error("Invalid element: " + myElement);
    return null;
  }

  @NotNull
  private Collection<PsiFileSystemItem> getContextByFile(@NotNull PsiFile file) {
    final PsiElement context = file.getContext();
    if (context != null) file = context.getContainingFile();

    Collection<PsiFileSystemItem> folders = getIncludingFileContexts(file);
    if (folders != null) return folders;

    return getContextByFileSystemItem(file.getOriginalFile());
  }

  @NotNull
  protected Collection<PsiFileSystemItem> getContextByFileSystemItem(@NotNull PsiFileSystemItem file) {
    VirtualFile virtualFile = file.getVirtualFile();
    if (virtualFile != null && FileBasedIndex.getInstance().getFileBeingCurrentlyIndexed() == null) {
      final FileReferenceHelper[] helpers = FileReferenceHelperRegistrar.getHelpers();
      final ArrayList<PsiFileSystemItem> list = new ArrayList<>();
      final Project project = file.getProject();
      boolean hasRealContexts = false;
      for (FileReferenceHelper helper : helpers) {
        if (helper.isMine(project, virtualFile)) {
          if (!list.isEmpty() && helper.isFallback()) {
            continue;
          }
          Collection<PsiFileSystemItem> contexts = helper.getContexts(project, virtualFile);
          for (PsiFileSystemItem context : contexts) {
            list.add(context);
            hasRealContexts |= !(context instanceof FileReferenceResolver);
          }
        }
      }
      if (!list.isEmpty()) {
        if (!hasRealContexts) {
          list.addAll(getParentDirectoryContext());
        }
        return list;
      }
      return getParentDirectoryContext();
    }
    return emptyList();
  }

  @Nullable
  private Collection<PsiFileSystemItem> getIncludingFileContexts(@NotNull PsiFile file) {
    if (useIncludingFileAsContext()) {
      FileContextProvider contextProvider = FileContextProvider.getProvider(file);
      if (contextProvider != null) {
        Collection<PsiFileSystemItem> folders = contextProvider.getContextFolders(file);
        if (!folders.isEmpty()) {
          return folders;
        }
        PsiFile contextFile = contextProvider.getContextFile(file);
        if (contextFile != null && contextFile.getParent() != null) {
          return singletonList(contextFile.getParent());
        }
      }
    }
    return null;
  }

  @NotNull
  protected Collection<PsiFileSystemItem> getParentDirectoryContext() {
    PsiFile file = getContainingFile();
    VirtualFile virtualFile = file == null ? null : file.getOriginalFile().getVirtualFile();
    final VirtualFile parent = virtualFile == null ? null : virtualFile.getParent();
    final PsiDirectory directory = parent == null ? null :file.getManager().findDirectory(parent);
    return directory != null ? singleton(directory) : emptyList();
  }

  /**
   * Finds file target contexts, locations where users can create a file. Includes only local file directory items.
   */
  public Collection<FileTargetContext> getTargetContexts() {
    PsiFile file = getContainingFile();
    if (file == null) return emptyList();

    Collection<FileTargetContext> customizationContexts = getTargetCustomizationContexts(file);

    Collection<FileTargetContext> targetContexts;
    if (customizationContexts != null) {
      targetContexts = customizationContexts;
    } else {
      targetContexts = getTargetContextByFile(file);
    }

    // CreateFilePathFix and CreateDirectoryPathFix support only local files
    return filterLocalFsContexts(targetContexts);
  }

  private static Collection<FileTargetContext> filterLocalFsContexts(Collection<? extends FileTargetContext> contexts) {
    return ContainerUtil.filter(contexts, c -> {
      VirtualFile file = c.getFileSystemItem().getVirtualFile();
      return file != null && c.getFileSystemItem().isDirectory() && file.isInLocalFileSystem();
    });
  }

  @Nullable
  private Collection<PsiFileSystemItem> getCustomizationContexts(PsiFile file) {
    if (myOptions != null) {
      Function<PsiFile, Collection<PsiFileSystemItem>> value = DEFAULT_PATH_EVALUATOR_OPTION.getValue(myOptions);
      if (value != null) {
        final Collection<PsiFileSystemItem> roots = value.fun(file);
        if (roots != null) {
          for (PsiFileSystemItem root : roots) {
            if (root == null) {
              LOG.error("Default path evaluator " + value + " produced a null root for " + file);
            }
          }
          return roots;
        }
      }
    }
    return null;
  }

  @Nullable
  private Collection<FileTargetContext> getTargetCustomizationContexts(@NotNull PsiFile file) {
    if (myOptions != null) {
      Function<PsiFile, Collection<PsiFileSystemItem>> value = DEFAULT_PATH_EVALUATOR_OPTION.getValue(myOptions);
      if (value != null) {
        Collection<FileTargetContext> roots;
        if (value instanceof TargetContextEvaluator) {
          roots = ((TargetContextEvaluator)value).getTargetContexts(file);
        }
        else {
          Collection<PsiFileSystemItem> items = value.fun(file);
          roots = items != null ? toTargetContexts(items) : emptyList();
        }

        return roots;
      }
    }
    return null;
  }

  @NotNull
  private Collection<FileTargetContext> getTargetContextByFile(@NotNull PsiFile file) {
    boolean absolutePathReference = isAbsolutePathReference();

    if (!absolutePathReference) {
      PsiElement context = file.getContext();
      if (context != null) file = context.getContainingFile();

      Collection<PsiFileSystemItem> folders = getIncludingFileContexts(file);
      if (folders != null) return toTargetContexts(folders);
    }

    PsiDirectory parent = file.getParent();
    Module module = ModuleUtilCore.findModuleForPsiElement(parent == null ? file : parent);
    if (absolutePathReference && module == null) return emptyList();

    PsiFileSystemItem originalFile = file.getOriginalFile();
    VirtualFile virtualFile = originalFile.getVirtualFile();
    if (virtualFile == null) return emptyList();

    // reference helpers may provide non-unique results, use LinkedHashSet to collapse them preserving the order
    Set<FileTargetContext> list = new LinkedHashSet<>();

    Project project = originalFile.getProject();
    boolean hasRealContexts = false;

    FileReferenceHelper[] helpers = FileReferenceHelperRegistrar.getHelpers();
    for (FileReferenceHelper helper : helpers) {
      if (helper.isMine(project, virtualFile)) {
        if (!list.isEmpty() && helper.isFallback()) {
          continue;
        }

        Collection<FileTargetContext> contexts = helper.getTargetContexts(project, virtualFile, absolutePathReference);

        for (FileTargetContext context : contexts) {
          list.add(context);
          hasRealContexts |= !(context.getFileSystemItem() instanceof FileReferenceResolver);
        }
      }
    }

    if (!list.isEmpty()) {
      if (!hasRealContexts) {
        for (PsiFileSystemItem item : getParentDirectoryContext()) {
          list.add(new FileTargetContext(item));
        }
      }
      return list;
    }
    return toTargetContexts(getParentDirectoryContext());
  }

  public @NlsSafe String getPathString() {
    return myPathString;
  }

  public boolean isAbsolutePathReference() {
    return myPathString.startsWith(getSeparatorString());
  }

  protected boolean useIncludingFileAsContext() {
    return true;
  }

  @Nullable
  public PsiFileSystemItem resolve() {
    final FileReference lastReference = getLastReference();
    return lastReference == null ? null : lastReference.resolve();
  }

  @Nullable
  public FileReference getLastReference() {
    return myReferences == null || myReferences.length == 0 ? null : myReferences[myReferences.length - 1];
  }

  @NotNull
  public static Collection<PsiFileSystemItem> getAbsoluteTopLevelDirLocations(@NotNull final PsiFile file) {
    final VirtualFile virtualFile = file.getVirtualFile();
    if (virtualFile == null) return emptyList();

    final PsiDirectory parent = file.getParent();
    final Module module = ModuleUtilCore.findModuleForPsiElement(parent == null ? file : parent);
    if (module == null) return emptyList();

    Set<PsiFileSystemItem> result = new LinkedHashSet<>();
    final Project project = file.getProject();
    for (FileReferenceHelper helper : FileReferenceHelperRegistrar.getHelpers()) {
      if (helper.isMine(project, virtualFile)) {
        if (helper.isFallback() && !result.isEmpty()) {
          continue;
        }
        Collection<PsiFileSystemItem> roots = helper.getRoots(module, virtualFile);
        for (PsiFileSystemItem root : roots) {
          if (root == null) {
            LOG.error("Helper " + helper + " produced a null root for " + file);
          }
        }
        result.addAll(roots);
      }
    }
    return new ArrayList<>(result);
  }

  @NotNull
  private static Collection<FileTargetContext> getTargetAbsoluteTopLevelContexts(@NotNull PsiFile file) {
    VirtualFile virtualFile = file.getVirtualFile();
    if (virtualFile == null) return emptyList();

    PsiDirectory parent = file.getParent();
    Module module = ModuleUtilCore.findModuleForPsiElement(parent == null ? file : parent);
    if (module == null) return emptyList();

    Set<FileTargetContext> result = new LinkedHashSet<>();
    Project project = file.getProject();
    for (FileReferenceHelper helper : FileReferenceHelperRegistrar.getHelpers()) {
      if (helper.isMine(project, virtualFile)) {
        if (helper.isFallback() && !result.isEmpty()) {
          continue;
        }
        Collection<FileTargetContext> roots = helper.getTargetContexts(project, virtualFile, true);
        result.addAll(roots);
      }
    }
    return new ArrayList<>(result);
  }

  @NotNull
  protected Collection<PsiFileSystemItem> toFileSystemItems(VirtualFile... files) {
    return toFileSystemItems(Arrays.asList(files));
  }

  @NotNull
  protected Collection<PsiFileSystemItem> toFileSystemItems(@NotNull Collection<? extends VirtualFile> files) {
    final PsiManager manager = getElement().getManager();
    return ContainerUtil.mapNotNull(files, file -> file != null && file.isValid() ? manager.findDirectory(file) : null);
  }

  protected Condition<PsiFileSystemItem> getReferenceCompletionFilter() {
    return Conditions.alwaysTrue();
  }

  public <Option> void addCustomization(CustomizableReferenceProvider.CustomizationKey<Option> key, Option value) {
    if (myOptions == null) {
      myOptions = new HashMap<>(5);
    }
    myOptions.put(key, value);
  }

  public boolean couldBeConvertedTo(final boolean relative) {
    return true;
  }

  public boolean absoluteUrlNeedsStartSlash() {
    return true;
  }

  public FileType @NotNull [] getSuitableFileTypes() {
    return mySuitableFileTypes == null ? EMPTY_FILE_TYPES : mySuitableFileTypes;
  }

  public boolean isEmptyPathAllowed() {
    return myEmptyPathAllowed;
  }

  public void setEmptyPathAllowed(boolean emptyPathAllowed) {
    myEmptyPathAllowed = emptyPathAllowed;
  }

  public boolean supportsExtendedCompletion() {
    return true;
  }

  /**
   * Enables custom handling of target locations for {@link #DEFAULT_PATH_EVALUATOR_OPTION} in customizations.
   */
  public interface TargetContextEvaluator {
    /**
     * Returns target file locations for "Create File" quick fixes.
     */
    @NotNull
    Collection<FileTargetContext> getTargetContexts(@NotNull PsiFile file);
  }

  private static class AbsoluteTopLevelEvaluator implements Function<PsiFile, Collection<PsiFileSystemItem>>, TargetContextEvaluator {
    @Override
    @NotNull
    public Collection<FileTargetContext> getTargetContexts(@NotNull PsiFile file) {
      return getTargetAbsoluteTopLevelContexts(file);
    }

    @Override
    public Collection<PsiFileSystemItem> fun(PsiFile file) {
      return getAbsoluteTopLevelDirLocations(file);
    }
  }
}
