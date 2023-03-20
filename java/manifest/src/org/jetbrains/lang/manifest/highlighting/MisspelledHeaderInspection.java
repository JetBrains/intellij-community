/*
 * Copyright (c) 2007-2009, Osmorc Development Team
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 *     * Redistributions of source code must retain the above copyright notice, this list
 *       of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above copyright notice, this
 *       list of conditions and the following disclaimer in the documentation and/or other
 *       materials provided with the distribution.
 *     * Neither the name of 'Osmorc Development Team' nor the names of its contributors may be
 *       used to endorse or promote products derived from this software without specific
 *       prior written permission.
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY
 * EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL
 * THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT
 * OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
 * HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR
 * TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE,
 * EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.jetbrains.lang.manifest.highlighting;

import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.options.OptPane;
import com.intellij.openapi.project.Project;
import com.intellij.profile.codeInspection.ProjectInspectionProfileManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiFile;
import com.intellij.spellchecker.engine.Suggestion;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.text.EditDistance;
import com.intellij.util.xmlb.annotations.XCollection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.lang.manifest.ManifestBundle;
import org.jetbrains.lang.manifest.header.HeaderParserRepository;
import org.jetbrains.lang.manifest.psi.Header;

import java.util.*;

import static com.intellij.codeInspection.options.OptPane.pane;

/**
 * @author Robert F. Beeger (robert@beeger.net)
 */
public final class MisspelledHeaderInspection extends LocalInspectionTool {
  private static final int MAX_SUGGESTIONS = 5;
  private static final int MAX_DISTANCE = 4;
  private static final int TYPO_DISTANCE = 2;

  @XCollection(elementName = "header")
  public final List<String> CUSTOM_HEADERS = new ArrayList<>();

  private final HeaderParserRepository myRepository;

  public MisspelledHeaderInspection() {
    myRepository = HeaderParserRepository.getInstance();
  }

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, boolean isOnTheFly) {
    return new PsiElementVisitor() {
      @Override
      public void visitElement(@NotNull PsiElement element) {
        if (element instanceof Header header) {
          String headerName = header.getName();

          SortedSet<Suggestion> matches = new TreeSet<>();
          addMatches(headerName, CUSTOM_HEADERS, matches);
          addMatches(headerName, myRepository.getAllHeaderNames(), matches);

          Suggestion bestMatch = ContainerUtil.getFirstItem(matches);
          if (bestMatch != null && headerName.equals(bestMatch.getWord())) {
            return;
          }

          List<LocalQuickFix> fixes = new ArrayList<>();
          for (Suggestion match : matches) {
            fixes.add(new HeaderRenameQuickFix(header, match.getWord()));
            if (fixes.size() == MAX_SUGGESTIONS) break;
          }
          if (bestMatch == null || bestMatch.getMetrics() > TYPO_DISTANCE) {
            fixes.add(new CustomHeaderQuickFix(header, CUSTOM_HEADERS));
          }
          holder.registerProblem(
            header.getNameElement(), ManifestBundle.message("inspection.header.message"),
            ProblemHighlightType.GENERIC_ERROR_OR_WARNING, fixes.toArray(LocalQuickFix.EMPTY_ARRAY)
          );
        }
      }

      private static void addMatches(String headerName, Collection<String> headers, SortedSet<? super Suggestion> matches) {
        for (String candidate : headers) {
          int distance = EditDistance.optimalAlignment(headerName, candidate, false, MAX_DISTANCE);
          if (distance <= MAX_DISTANCE) {
            matches.add(new Suggestion(candidate, distance));
          }
        }
      }
    };
  }

  @Override
  public @NotNull OptPane getOptionsPane() {
    return pane(OptPane.stringList("CUSTOM_HEADERS", ManifestBundle.message("inspection.header.ui.label")));
  }

  private static final class HeaderRenameQuickFix extends AbstractManifestQuickFix {
    private final String myNewName;

    private HeaderRenameQuickFix(Header header, String newName) {
      super(header);
      myNewName = newName;
    }

    @NotNull
    @Override
    public String getText() {
      return ManifestBundle.message("inspection.header.rename.fix", myNewName);
    }

    @Override
    public void invoke(@NotNull Project project, @NotNull PsiFile file, @NotNull PsiElement startElement, @NotNull PsiElement endElement) {
      ((Header)startElement).setName(myNewName);
    }
  }

  private static final class CustomHeaderQuickFix extends AbstractManifestQuickFix {
    private final String myHeaderName;
    private final Collection<String> myHeaders;

    private CustomHeaderQuickFix(Header header, Collection<String> headers) {
      super(header);
      myHeaderName = header.getName();
      myHeaders = headers;
    }

    @NotNull
    @Override
    public String getText() {
      return ManifestBundle.message("inspection.header.remember.fix", myHeaderName);
    }

    @Override
    public void invoke(@NotNull Project project, @NotNull PsiFile file, @NotNull PsiElement startElement, @NotNull PsiElement endElement) {
      myHeaders.add(myHeaderName);

      ProjectInspectionProfileManager.getInstance(project).fireProfileChanged();
    }

    @Override
    public @NotNull IntentionPreviewInfo generatePreview(@NotNull Project project, @NotNull ProblemDescriptor previewDescriptor) {
      List<String> updated = new ArrayList<>(myHeaders);
      updated.add(myHeaderName);
      return IntentionPreviewInfo.addListOption(updated, myHeaderName, ManifestBundle.message("inspection.header.ui.label"));
    }

    @Override
    public boolean startInWriteAction() {
      return false;
    }
  }
}
