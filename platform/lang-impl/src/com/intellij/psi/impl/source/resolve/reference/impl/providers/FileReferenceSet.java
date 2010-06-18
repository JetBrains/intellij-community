/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiModificationTracker;
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
  private static final char SEPARATOR = '/';
  private static final String SEPARATOR_STRING = "/";
  private static final Key<CachedValue<Collection<PsiFileSystemItem>>> DEFAULT_CONTEXTS_KEY = new Key<CachedValue<Collection<PsiFileSystemItem>>>("default file contexts");
  public static final CustomizableReferenceProvider.CustomizationKey<Function<PsiFile, Collection<PsiFileSystemItem>>> DEFAULT_PATH_EVALUATOR_OPTION =
    new CustomizableReferenceProvider.CustomizationKey<Function<PsiFile, Collection<PsiFileSystemItem>>>(PsiBundle.message("default.path.evaluator.option"));
  public static final Function<PsiFile, Collection<PsiFileSystemItem>> ABSOLUTE_TOP_LEVEL = new Function<PsiFile, Collection<PsiFileSystemItem>>() {
          @Nullable
          public Collection<PsiFileSystemItem> fun(final PsiFile file) {
            return getAbsoluteTopLevelDirLocations(file);
          }
        };

   public static final Condition<PsiFileSystemItem> FILE_FILTER = new Condition<PsiFileSystemItem>() {
    public boolean value(final PsiFileSystemItem item) {
      return item instanceof PsiFile;
    }
  };

  public static final Condition<PsiFileSystemItem> DIRECTORY_FILTER = new Condition<PsiFileSystemItem>() {
    public boolean value(final PsiFileSystemItem item) {
      return item instanceof PsiDirectory;
    }
  };

  private FileReference[] myReferences;
  private PsiElement myElement;
  private final int myStartInElement;
  private final boolean myCaseSensitive;
  private final String myPathString;
  private Collection<PsiFileSystemItem> myDefaultContexts;
  private final boolean myEndingSlashNotAllowed;
  private boolean myEmptyPathAllowed;
  private @Nullable Map<CustomizableReferenceProvider.CustomizationKey, Object> myOptions;
  private @Nullable FileType[] mySuitableFileTypes;

  public FileReferenceSet(String str,
                          PsiElement element,
                          int startInElement,
                          PsiReferenceProvider provider,
                          boolean caseSensitive,
                          boolean endingSlashNotAllowed,
                          @Nullable FileType[] suitableFileTypes) {
    myElement = element;
    myStartInElement = startInElement;
    myCaseSensitive = caseSensitive;
    myPathString = str.trim();
    myEndingSlashNotAllowed = endingSlashNotAllowed;
    myEmptyPathAllowed = !endingSlashNotAllowed;
    myOptions = provider instanceof CustomizableReferenceProvider ? ((CustomizableReferenceProvider)provider).getOptions() : null;
    mySuitableFileTypes = suitableFileTypes;

    reparse(str);
  }

  public static FileReferenceSet createSet(PsiElement element, final boolean soft, boolean endingSlashNotAllowed, final boolean urlEncoded) {

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
      protected boolean isUrlEncoded() {
        return urlEncoded;
      }

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

  public FileReferenceSet(final @NotNull PsiElement element) {

    myElement = element;
    final ElementManipulator<PsiElement> manipulator = ElementManipulators.getManipulator(element);
    final TextRange range = manipulator.getRangeInElement(element);
    myStartInElement = range.getStartOffset();
    final String s = range.substring(element.getText());
    myPathString = s.trim();
    myEndingSlashNotAllowed = true;
    myCaseSensitive = false;

    reparse(s);
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

  private void reparse(String str) {
    final List<FileReference> referencesList = new ArrayList<FileReference>();
    // skip white space
    int currentSlash = -1;
    while (currentSlash + 1 < str.length() && Character.isWhitespace(str.charAt(currentSlash + 1))) currentSlash++;
    if (currentSlash + 1 < str.length() && str.charAt(currentSlash + 1) == SEPARATOR) currentSlash++;
    int index = 0;

    if (str.equals(SEPARATOR_STRING)) {
      final FileReference fileReference =
        createFileReference(new TextRange(myStartInElement, myStartInElement + 1), index++, SEPARATOR_STRING);
      referencesList.add(fileReference);
    }

    while (true) {
      final int nextSlash = str.indexOf(SEPARATOR, currentSlash + 1);
      final String subreferenceText = nextSlash > 0 ? str.substring(currentSlash + 1, nextSlash) : str.substring(currentSlash + 1);
      final FileReference ref = createFileReference(
        new TextRange(myStartInElement + currentSlash + 1, myStartInElement + (nextSlash > 0 ? nextSlash : str.length())),
        index++,
        subreferenceText);
      referencesList.add(ref);
      if ((currentSlash = nextSlash) < 0) {
        break;
      }
    }

    setReferences(referencesList.toArray(new FileReference[referencesList.size()]));
  }

  private void setReferences(final FileReference[] references) {
    myReferences = references;
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
            LOG.assertTrue(root != null, "Default path evaluator " + value + " produced a null root for " + file);
          }
          return roots;
        }
      }
    }
    if (isAbsolutePathReference()) {
      return getAbsoluteTopLevelDirLocations(file);
    }

    final CachedValueProvider<Collection<PsiFileSystemItem>> myDefaultContextProvider = new CachedValueProvider<Collection<PsiFileSystemItem>>() {
      public Result<Collection<PsiFileSystemItem>> compute() {
        final Collection<PsiFileSystemItem> contexts = getContextByFile(file);
        return Result.createSingleDependency(contexts,
                                             PsiModificationTracker.OUT_OF_CODE_BLOCK_MODIFICATION_COUNT);
      }
    };
    final CachedValuesManager cachedValuesManager = CachedValuesManager.getManager(myElement.getProject());
    final Collection<PsiFileSystemItem> value =
      cachedValuesManager.getCachedValue(file, DEFAULT_CONTEXTS_KEY, myDefaultContextProvider, false);
    return value == null ? Collections.<PsiFileSystemItem>emptyList() : value;
  }

  @Nullable
  private PsiFile getContainingFile() {
    final PsiFile file = InjectedLanguageUtil.getTopLevelFile(myElement.getContainingFile());
    if (file == null) {
      LOG.error("Invalid element: " + myElement);
    }

    return file.getOriginalFile();
  }

  @Nullable
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
          list.addAll(helper.getContexts(project, virtualFile));  
        }
      }
      if (list.size() > 0) {
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
    return myPathString.startsWith(SEPARATOR_STRING);
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
  private static Collection<PsiFileSystemItem> getAbsoluteTopLevelDirLocations(final @NotNull PsiFile file) {

    final VirtualFile virtualFile = file.getVirtualFile();
    if (virtualFile == null) {
      return Collections.emptyList();
    }
    final Project project = file.getProject();
    PsiDirectory parent = file.getParent();
    final Module module = ModuleUtil.findModuleForPsiElement(parent == null ? file : parent);
    if (module == null) {
      return Collections.emptyList();
    }
    final FileReferenceHelper[] helpers = FileReferenceHelperRegistrar.getHelpers();
    final ArrayList<PsiFileSystemItem> list = new ArrayList<PsiFileSystemItem>();
    for (FileReferenceHelper helper : helpers) {
      if (helper.isMine(project, virtualFile)) {
        final Collection<PsiFileSystemItem> roots = helper.getRoots(module);
        for (PsiFileSystemItem root : roots) {
          LOG.assertTrue(root != null, "Helper " + helper + " produced a null root for " + file);
        }
        list.addAll(roots);
      }
    }

    if (list.size() == 0) {
      list.addAll(FileReferenceHelperRegistrar.getNotNullHelper(file).getRoots(module));
    }
    return list;
  }

  protected Condition<PsiFileSystemItem> getReferenceCompletionFilter() {
    return Condition.TRUE;
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
