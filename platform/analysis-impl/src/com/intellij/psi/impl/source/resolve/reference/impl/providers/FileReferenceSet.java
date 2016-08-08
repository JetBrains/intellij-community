/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

package com.intellij.psi.impl.source.resolve.reference.impl.providers;

import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Conditions;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.util.Function;
import com.intellij.util.NullableFunction;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author Maxim.Mossienko
 */
public class FileReferenceSet {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReferenceSet");

  private static final FileType[] EMPTY_FILE_TYPES = {};

  public static final CustomizableReferenceProvider.CustomizationKey<Function<PsiFile, Collection<PsiFileSystemItem>>>
    DEFAULT_PATH_EVALUATOR_OPTION =
    new CustomizableReferenceProvider.CustomizationKey<>(
      PsiBundle.message("default.path.evaluator.option"));
  public static final Function<PsiFile, Collection<PsiFileSystemItem>> ABSOLUTE_TOP_LEVEL =
    file -> getAbsoluteTopLevelDirLocations(file);

  public static final Condition<PsiFileSystemItem> FILE_FILTER = item -> item instanceof PsiFile;

  public static final Condition<PsiFileSystemItem> DIRECTORY_FILTER = item -> item instanceof PsiDirectory;

  protected FileReference[] myReferences;
  private PsiElement myElement;
  private final int myStartInElement;
  private final boolean myCaseSensitive;
  private final String myPathStringNonTrimmed;
  private final String myPathString;
  private Collection<PsiFileSystemItem> myDefaultContexts;
  private final boolean myEndingSlashNotAllowed;
  private boolean myEmptyPathAllowed;
  @Nullable private Map<CustomizableReferenceProvider.CustomizationKey, Object> myOptions;
  @Nullable private FileType[] mySuitableFileTypes;

  public FileReferenceSet(String str,
                          @NotNull PsiElement element,
                          int startInElement,
                          PsiReferenceProvider provider,
                          boolean caseSensitive,
                          boolean endingSlashNotAllowed,
                          @Nullable FileType[] suitableFileTypes) {
    this(str, element, startInElement, provider, caseSensitive, endingSlashNotAllowed, suitableFileTypes, true);
  }

  public FileReferenceSet(String str,
                          @NotNull PsiElement element,
                          int startInElement,
                          PsiReferenceProvider provider,
                          boolean caseSensitive,
                          boolean endingSlashNotAllowed,
                          @Nullable FileType[] suitableFileTypes,
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
   * This should be removed. Please use {@link FileReference#getContexts()} instead.
   */
  @Deprecated
  protected Collection<PsiFileSystemItem> getExtraContexts() {
    return Collections.emptyList();
  }

  public static FileReferenceSet createSet(@NotNull PsiElement element,
                                           final boolean soft,
                                           boolean endingSlashNotAllowed,
                                           final boolean urlEncoded) {

    final ElementManipulator<PsiElement> manipulator = ElementManipulators.getManipulator(element);
    assert manipulator != null;
    final TextRange range = manipulator.getRangeInElement(element);
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

  public FileReference createFileReference(final TextRange range, final int index, final String text) {
    return new FileReference(this, range, index, text);
  }

  protected void reparse() {
    List<FileReference> referencesList = reparse(myPathStringNonTrimmed, myStartInElement);
    myReferences = referencesList.toArray(new FileReference[referencesList.size()]);
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
    List<FileReference> referencesList = ContainerUtil.newArrayList();

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
      referencesList.add(createFileReference(r, index ++, decoded.subSequence(curSep, curSep + sepLen).toString()));
    }
    curSep = curSep == wsHead ? curSep + sepLen : wsHead; // reset offsets & start again for simplicity
    sepLen = 0;
    while (curSep >= 0) {
      int nextSep = findSeparatorOffset(decoded, curSep + sepLen);
      int start = curSep + sepLen;
      int endTrimmed = nextSep > 0 ? nextSep : Math.max(start, decoded.length() - wsTail);
      int endInclusive = nextSep > 0 ? nextSep : Math.max(start, decoded.length() - 1 - wsTail);
      // todo move ${placeholder} support (the str usage below) to a reference implementation
      // todo reference-set should be bound to exact range & text in a file, consider: ${slash}path${slash}file&amp;.txt
      String refText = index == 0 && nextSep < 0 && !StringUtil.contains(decoded, str) ? str :
                                decoded.subSequence(start, endTrimmed).toString();
      TextRange r = new TextRange(offset(start, escaper, valueRange),
                                  offset(endInclusive, escaper, valueRange) + (nextSep < 0 && refText.length() > 0 ? 1 : 0));
      referencesList.add(createFileReference(r, index++, refText));
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

  @NotNull
  public FileReference[] getAllReferences() {
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
    if (myDefaultContexts == null) {
      myDefaultContexts = computeDefaultContexts();
    }
    return myDefaultContexts;
  }

  @NotNull
  public Collection<PsiFileSystemItem> computeDefaultContexts() {
    final PsiFile file = getContainingFile();
    if (file == null) return Collections.emptyList();

    if (myOptions != null) {
      final Function<PsiFile, Collection<PsiFileSystemItem>> value = DEFAULT_PATH_EVALUATOR_OPTION.getValue(myOptions);
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

    if (useIncludingFileAsContext()) {
      final FileContextProvider contextProvider = FileContextProvider.getProvider(file);
      if (contextProvider != null) {
        final Collection<PsiFileSystemItem> folders = contextProvider.getContextFolders(file);
        if (!folders.isEmpty()) {
          return folders;
        }
        final PsiFile contextFile = contextProvider.getContextFile(file);
        if (contextFile != null) {
          return Collections.singleton(contextFile.getParent());
        }
      }
    }

    return getContextByFileSystemItem(file.getOriginalFile());
  }

  @NotNull
  protected final Collection<PsiFileSystemItem> getContextByFileSystemItem(@NotNull PsiFileSystemItem file) {
    VirtualFile virtualFile = file.getVirtualFile();
    if (virtualFile != null) {
      final FileReferenceHelper[] helpers = FileReferenceHelperRegistrar.getHelpers();
      final ArrayList<PsiFileSystemItem> list = new ArrayList<>();
      final Project project = file.getProject();
      for (FileReferenceHelper helper : helpers) {
        if (helper.isMine(project, virtualFile)) {
          if (!list.isEmpty() && helper.isFallback()) {
            continue;
          }
          list.addAll(helper.getContexts(project, virtualFile));
        }
      }
      if (!list.isEmpty()) {
        return list;
      }
      final VirtualFile parent = virtualFile.getParent();
      if (parent != null) {
        final PsiDirectory directory = file.getManager().findDirectory(parent);
        if (directory != null) {
          return Collections.singleton(directory);
        }
      }
    }
    return Collections.emptyList();
  }

  public String getPathString() {
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
    if (virtualFile == null) return Collections.emptyList();

    final PsiDirectory parent = file.getParent();
    final Module module = ModuleUtilCore.findModuleForPsiElement(parent == null ? file : parent);
    if (module == null) return Collections.emptyList();

    final List<PsiFileSystemItem> list = new ArrayList<>();
    final Project project = file.getProject();
    for (FileReferenceHelper helper : FileReferenceHelperRegistrar.getHelpers()) {
      if (helper.isMine(project, virtualFile)) {
        if (helper.isFallback() && !list.isEmpty()) {
          continue;
        }
        final Collection<PsiFileSystemItem> roots = helper.getRoots(module);
        for (PsiFileSystemItem root : roots) {
          if (root == null) {
            LOG.error("Helper " + helper + " produced a null root for " + file);
          }
        }
        list.addAll(roots);
      }
    }
    return list;
  }

  @NotNull
  protected Collection<PsiFileSystemItem> toFileSystemItems(VirtualFile... files) {
    return toFileSystemItems(Arrays.asList(files));
  }

  @NotNull
  protected Collection<PsiFileSystemItem> toFileSystemItems(@NotNull Collection<VirtualFile> files) {
    final PsiManager manager = getElement().getManager();
    return ContainerUtil.mapNotNull(files, (NullableFunction<VirtualFile, PsiFileSystemItem>)file -> file != null ? manager.findDirectory(file) : null);
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

  @NotNull
  public FileType[] getSuitableFileTypes() {
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
}
