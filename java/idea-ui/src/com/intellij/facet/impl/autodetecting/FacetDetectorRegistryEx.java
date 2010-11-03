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

package com.intellij.facet.impl.autodetecting;

import com.intellij.facet.Facet;
import com.intellij.facet.FacetConfiguration;
import com.intellij.facet.FacetModel;
import com.intellij.facet.FacetType;
import com.intellij.facet.autodetecting.DetectedFacetPresentation;
import com.intellij.facet.autodetecting.FacetDetector;
import com.intellij.facet.autodetecting.FacetDetectorRegistry;
import com.intellij.facet.autodetecting.UnderlyingFacetSelector;
import com.intellij.ide.caches.FileContent;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileFilter;
import com.intellij.patterns.*;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.cache.impl.CacheUtil;
import com.intellij.util.text.CharSequenceReader;
import com.intellij.util.xml.NanoXmlUtil;
import com.intellij.util.xml.XmlFileHeader;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Collection;

/**
 * @author nik
 */
public class FacetDetectorRegistryEx<C extends FacetConfiguration> implements FacetDetectorRegistry<C> {
  private final @Nullable FacetDetectorForWizardRegistry<C> myForWizardDelegate;
  private final @Nullable FacetOnTheFlyDetectorRegistry<C> myOnTheFlyDelegate;
  private DetectedFacetPresentation myPresentation;

  public FacetDetectorRegistryEx(final @Nullable FacetDetectorForWizardRegistry<C> forWizardDelegate, final @Nullable FacetOnTheFlyDetectorRegistry<C> onTheFlyDelegate) {
    myForWizardDelegate = forWizardDelegate;
    myOnTheFlyDelegate = onTheFlyDelegate;
  }

  public void customizeDetectedFacetPresentation(@NotNull final DetectedFacetPresentation presentation) {
    myPresentation = presentation;
  }

  public void registerUniversalDetectorByRootTag(@NotNull final String rootTag, @NotNull final FacetDetector<VirtualFile, C> detector) {
    registerUniversalDetector(StdFileTypes.XML, new VirtualFileFilter() {
      @Override
      public boolean accept(VirtualFile file) {
        try {
          return rootTag.equals(NanoXmlUtil.parseHeaderWithException(file).getRootTagLocalName());
        }
        catch (IOException e) {
          return false;
        }
      }
    }, new FileContentFilter() {
      @Override
      public boolean accept(FileContent fileContent) {
        try {
          return rootTag.equals(parseHeaderWithException(fileContent).getRootTagLocalName());
        }
        catch (IOException e) {
          return false;
        }
      }
    }, detector);
  }

  @NotNull
  private static XmlFileHeader parseHeaderWithException(FileContent fileContent) throws IOException {
    final CharSequence contentText = CacheUtil.getContentText(fileContent);
    //noinspection IOResourceOpenedButNotSafelyClosed
    return NanoXmlUtil.parseHeaderWithException(new CharSequenceReader(contentText));
  }

  public <U extends FacetConfiguration> void registerUniversalDetectorByFileNameAndRootTag(@NotNull @NonNls String fileName,
                                                            @NotNull @NonNls String rootTag,
                                                            @NotNull final FacetDetector<VirtualFile, C> detector,
                                                            @Nullable UnderlyingFacetSelector<VirtualFile, U> underlyingFacetSelector) {
    VirtualFilePattern fileNamePattern = PlatformPatterns.virtualFile().withName(StandardPatterns.string().equalTo(fileName));
    VirtualFilePattern wizardPattern = fileNamePattern.xmlWithRootTag(StandardPatterns.string().equalTo(rootTag));

    if (underlyingFacetSelector != null) {
      registerUniversalSubFacetDetector(StdFileTypes.XML, wizardPattern, detector, underlyingFacetSelector);
    }
    else {
      registerUniversalDetector(StdFileTypes.XML, wizardPattern, detector);
    }
  }

  public void registerDetectorForWizard(@NotNull final FileType fileType, @NotNull final VirtualFileFilter virtualFileFilter, @NotNull final FacetDetector<VirtualFile, C> facetDetector) {
    if (myForWizardDelegate != null) {
      myForWizardDelegate.register(fileType, virtualFileFilter, facetDetector);
    }
  }

  public void registerDetectorForWizard(@NotNull final FileType fileType, @NotNull final VirtualFilePattern virtualFilePattern,
                       @NotNull final FacetDetector<VirtualFile, C> facetDetector) {
    registerDetectorForWizard(fileType, new MyPatternFilter(virtualFilePattern), facetDetector);
  }

  public <U extends FacetConfiguration> void registerSubFacetDetectorForWizard(@NotNull final FileType fileType, @NotNull final VirtualFilePattern virtualFilePattern,
                       @NotNull final FacetDetector<VirtualFile, C> facetDetector,
                       @NotNull final UnderlyingFacetSelector<VirtualFile, U> underlyingFacetSelector) {
    if (myForWizardDelegate != null) {
      myForWizardDelegate.register(fileType, new MyPatternFilter(virtualFilePattern), facetDetector, underlyingFacetSelector);
    }
  }


  private <U extends FacetConfiguration> void registerOnTheFlyDetector(@NotNull final FileType fileType,
                                                                      @NotNull final VirtualFileFilter virtualFileFilter,
                                                                      @NotNull final Condition<PsiFile> psiFileFilter,
                                                                      @NotNull final FacetDetector<PsiFile, C> facetDetector,
                                                                      @Nullable UnderlyingFacetSelector<VirtualFile, U> selector) {
    if (myOnTheFlyDelegate != null) {
      myOnTheFlyDelegate.register(fileType, new MyFileContentFilter(virtualFileFilter), psiFileFilter, facetDetector, selector);
    }
  }

  public <U extends FacetConfiguration> void registerOnTheFlySubFacetDetector(@NotNull final FileType fileType,
                                                                              @NotNull final VirtualFilePattern virtualFilePattern,
                                                                              @NotNull final ElementPattern<? extends PsiFile> psiFilePattern,
                                                                              @NotNull final FacetDetector<PsiFile, C> facetDetector,
                                                                              final UnderlyingFacetSelector<VirtualFile, U> selector) {
    registerOnTheFlyDetector(fileType, new MyPatternFilter(virtualFilePattern), new Condition<PsiFile>() {
      public boolean value(final PsiFile psiFile) {
        return psiFilePattern.accepts(psiFile);
      }
    }, facetDetector, selector);
  }

  public void registerOnTheFlyDetector(@NotNull final FileType fileType, @NotNull final VirtualFilePattern virtualFilePattern,
                       @NotNull final ElementPattern<? extends PsiFile> psiFilePattern,
                       @NotNull final FacetDetector<PsiFile, C> facetDetector) {
    registerOnTheFlySubFacetDetector(fileType, virtualFilePattern, psiFilePattern, facetDetector, null);
  }

  public void registerOnTheFlyDetector(@NotNull final FileType fileType, @NotNull final VirtualFileFilter virtualFileFilter, @NotNull final Condition<PsiFile> psiFileFilter,
                                       @NotNull final FacetDetector<PsiFile, C> facetDetector) {
    registerOnTheFlyDetector(fileType, virtualFileFilter, psiFileFilter, facetDetector, null);
  }



  public void registerUniversalDetector(@NotNull final FileType fileType, @NotNull final VirtualFilePattern virtualFilePattern,
                                        @NotNull final FacetDetector<VirtualFile, C> facetDetector) {
    registerUniversalDetector(fileType, new MyPatternFilter(virtualFilePattern), facetDetector);
  }

  public void registerUniversalDetector(@NotNull final FileType fileType, @NotNull final VirtualFileFilter virtualFileFilter, @NotNull final FacetDetector<VirtualFile, C> facetDetector) {
    registerUniversalDetector(fileType, virtualFileFilter, new MyFileContentFilter(virtualFileFilter), facetDetector);
  }

  private void registerUniversalDetector(@NotNull final FileType fileType, @NotNull VirtualFileFilter virtualFileFilter, @NotNull final FileContentFilter fileContentFilter,
                                         @NotNull final FacetDetector<VirtualFile, C> facetDetector) {
    if (myForWizardDelegate != null) {
      myForWizardDelegate.register(fileType, virtualFileFilter, facetDetector);
    }
    if (myOnTheFlyDelegate != null) {
      myOnTheFlyDelegate.register(fileType, fileContentFilter, facetDetector, null);
    }
  }

  public <U extends FacetConfiguration> void registerUniversalSubFacetDetector(@NotNull final FileType fileType, @NotNull final VirtualFilePattern virtualFilePattern,
                                                                               @NotNull final FacetDetector<VirtualFile, C> facetDetector,
                                                                               final UnderlyingFacetSelector<VirtualFile, U> underlyingFacetSelector) {
    registerSubFacetDetectorForWizard(fileType, virtualFilePattern, facetDetector, underlyingFacetSelector);
    if (myOnTheFlyDelegate != null) {
      myOnTheFlyDelegate.register(fileType, new MyFileContentFilter(new MyPatternFilter(virtualFilePattern)), facetDetector, underlyingFacetSelector);
    }
  }

  private static class MyPatternFilter implements VirtualFileFilter {
    private final VirtualFilePattern myVirtualFilePattern;

    public MyPatternFilter(final VirtualFilePattern virtualFilePattern) {
      myVirtualFilePattern = virtualFilePattern;
    }

    public boolean accept(final VirtualFile file) {
      return myVirtualFilePattern.accepts(file);
    }
  }

  public static <C extends FacetConfiguration> FacetDetector<PsiFile, C> convertDetector(final FacetDetector<VirtualFile, C> detector) {
    return new FacetDetector<PsiFile, C>(detector.getId() + "-psi") {
      public C detectFacet(final PsiFile source, final Collection<C> existentFacetConfigurations) {
        VirtualFile virtualFile = source.getVirtualFile();
        return virtualFile != null ? detector.detectFacet(virtualFile, existentFacetConfigurations) : null;
      }

      public void beforeFacetAdded(@NotNull final Facet facet, final FacetModel facetModel, @NotNull final ModifiableRootModel modifiableRootModel) {
        detector.beforeFacetAdded(facet, facetModel, modifiableRootModel);
      }

      public void afterFacetAdded(@NotNull final Facet facet) {
        detector.afterFacetAdded(facet);
      }
    };
  }

  @NotNull
  public static <C extends FacetConfiguration> DetectedFacetPresentation getDetectedFacetPresentation(@NotNull FacetType<?,C> facetType) {
    FacetDetectorRegistryEx<C> registry = new FacetDetectorRegistryEx<C>(null, null);
    facetType.registerDetectors(registry);
    DetectedFacetPresentation presentation = registry.myPresentation;
    return presentation != null ? presentation : DefaultDetectedFacetPresentation.INSTANCE;
  }

  private static class MyFileContentFilter implements FileContentFilter {
    private final VirtualFileFilter myVirtualFileFilter;

    public MyFileContentFilter(VirtualFileFilter virtualFileFilter) {
      myVirtualFileFilter = virtualFileFilter;
    }

    @Override
    public boolean accept(FileContent fileContent) {
      return myVirtualFileFilter.accept(fileContent.getVirtualFile());
    }
  }
}
