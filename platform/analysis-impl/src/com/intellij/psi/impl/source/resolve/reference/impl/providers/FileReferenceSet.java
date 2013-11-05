/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.util.Function;
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
    new CustomizableReferenceProvider.CustomizationKey<Function<PsiFile, Collection<PsiFileSystemItem>>>(
      PsiBundle.message("default.path.evaluator.option"));
  public static final Function<PsiFile, Collection<PsiFileSystemItem>> ABSOLUTE_TOP_LEVEL =
    new Function<PsiFile, Collection<PsiFileSystemItem>>() {
      @Override
      @Nullable
      public Collection<PsiFileSystemItem> fun(final PsiFile file) {
        return getAbsoluteTopLevelDirLocations(file);
      }
    };

  public static final Condition<PsiFileSystemItem> FILE_FILTER = new Condition<PsiFileSystemItem>() {
    @Override
    public boolean value(final PsiFileSystemItem item) {
      return item instanceof PsiFile;
    }
  };

  public static final Condition<PsiFileSystemItem> DIRECTORY_FILTER = new Condition<PsiFileSystemItem>() {
    @Override
    public boolean value(final PsiFileSystemItem item) {
      return item instanceof PsiDirectory;
    }
  };

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
                          PsiElement element,
                          int startInElement,
                          PsiReferenceProvider provider,
                          boolean caseSensitive,
                          boolean endingSlashNotAllowed,
                          @Nullable FileType[] suitableFileTypes) {
    this(str, element, startInElement, provider, caseSensitive, endingSlashNotAllowed, suitableFileTypes, true);
  }

  public FileReferenceSet(String str,
                          PsiElement element,
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

  protected Collection<PsiFileSystemItem> getExtraContexts() {
    return Collections.emptyList();
  }

  public static FileReferenceSet createSet(PsiElement element,
                                           final boolean soft,
                                           boolean endingSlashNotAllowed,
                                           final boolean urlEncoded) {

    String text;
    int offset;

    final ElementManipulator<PsiElement> manipulator = ElementManipulators.getManipulator(element);
    assert manipulator != null;
    final TextRange range = manipulator.getRangeInElement(element);
    offset = range.getStartOffset();
    text = range.substring(element.getText());
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
                          PsiElement element,
                          int startInElement,
                          @Nullable PsiReferenceProvider provider,
                          final boolean isCaseSensitive) {
    this(str, element, startInElement, provider, isCaseSensitive, true);
  }


  public FileReferenceSet(@NotNull String str,
                          PsiElement element,
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


  public PsiElement getElement() {
    return myElement;
  }

  void setElement(final PsiElement element) {
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
    String str = myPathStringNonTrimmed;

    final List<FileReference> referencesList = reparse(str, myStartInElement);

    myReferences = referencesList.toArray(new FileReference[referencesList.size()]);
  }

  protected List<FileReference> reparse(String str, int startInElement) {
    final List<FileReference> referencesList = new ArrayList<FileReference>();


    String separatorString = getSeparatorString(); // separator's length can be more then 1 char
    int sepLen = separatorString.length();
    int currentSlash = -sepLen;

    // skip white space
    while (currentSlash + sepLen < str.length() && Character.isWhitespace(str.charAt(currentSlash + sepLen))) {
      currentSlash++;
    }

    if (currentSlash + sepLen + sepLen < str.length() &&
        str.substring(currentSlash + sepLen, currentSlash + sepLen + sepLen).equals(separatorString)) {
      currentSlash+=sepLen;
    }
    int index = 0;

    if (str.equals(separatorString)) {
      final FileReference fileReference =
        createFileReference(new TextRange(startInElement, startInElement + sepLen), index++, separatorString);
      referencesList.add(fileReference);
    }

    while (true) {
      final int nextSlash = str.indexOf(separatorString, currentSlash + sepLen);
      final String subreferenceText = nextSlash > 0 ? str.substring(currentSlash + sepLen, nextSlash) : str.substring(currentSlash + sepLen);
      final FileReference ref = createFileReference(
        new TextRange(startInElement + currentSlash + sepLen, startInElement + (nextSlash > 0 ? nextSlash : str.length())),
        index++,
        subreferenceText);
      referencesList.add(ref);
      if ((currentSlash = nextSlash) < 0) {
        break;
      }
    }
    return referencesList;
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
    final PsiFile file = InjectedLanguageManager.getInstance(cf.getProject()).getTopLevelFile(cf);
    if (file == null) {
      LOG.error("Invalid element: " + myElement);
    }

    return file.getOriginalFile();
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
          return Collections.<PsiFileSystemItem>singleton(contextFile.getParent());
        }
      }
    }

    VirtualFile virtualFile = file.getOriginalFile().getVirtualFile();

    if (virtualFile != null) {
      final FileReferenceHelper[] helpers = FileReferenceHelperRegistrar.getHelpers();
      final ArrayList<PsiFileSystemItem> list = new ArrayList<PsiFileSystemItem>();
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
          return Collections.<PsiFileSystemItem>singleton(directory);
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
    if (virtualFile == null) {
      return Collections.emptyList();
    }
    final Project project = file.getProject();
    PsiDirectory parent = file.getParent();
    final Module module = ModuleUtilCore.findModuleForPsiElement(parent == null ? file : parent);
    if (module == null) {
      return Collections.emptyList();
    }
    final FileReferenceHelper[] helpers = FileReferenceHelperRegistrar.getHelpers();
    final ArrayList<PsiFileSystemItem> list = new ArrayList<PsiFileSystemItem>();
    for (FileReferenceHelper helper : helpers) {
      if (helper.isMine(project, virtualFile)) {
        if (helper.isFallback() && !list.isEmpty()) {
          continue;
        }
        final Collection<PsiFileSystemItem> roots = helper.getRoots(module);
        for (PsiFileSystemItem root : roots) {
          LOG.assertTrue(root != null, "Helper " + helper + " produced a null root for " + file);
        }
        list.addAll(roots);
      }
    }

    return list;
  }

  protected Condition<PsiFileSystemItem> getReferenceCompletionFilter() {
    return Conditions.alwaysTrue();
  }

  public <Option> void addCustomization(CustomizableReferenceProvider.CustomizationKey<Option> key, Option value) {
    if (myOptions == null) {
      myOptions = new HashMap<CustomizableReferenceProvider.CustomizationKey, Object>(5);
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
}
