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

import com.intellij.codeInspection.*;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.lang.manifest.ManifestBundle;
import org.jetbrains.lang.manifest.header.HeaderNameMatch;
import org.jetbrains.lang.manifest.header.HeaderParserRepository;
import org.jetbrains.lang.manifest.psi.Header;

import java.util.Collection;
import java.util.List;

/**
 * @author Robert F. Beeger (robert@beeger.net)
 */
public class MisspelledHeaderInspection extends LocalInspectionTool {
  private static final int MAX_SUGGESTIONS = 10;

  private HeaderParserRepository myRepository;

  public MisspelledHeaderInspection() {
    myRepository = HeaderParserRepository.getInstance();
  }

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, boolean isOnTheFly) {
    return new PsiElementVisitor() {
      @Override
      public void visitElement(PsiElement element) {
        if (element instanceof Header) {
          Header header = (Header)element;
          Collection<HeaderNameMatch> matches = myRepository.getMatches(header.getName());
          if (!matches.isEmpty()) {
            List<HeaderNameSpellingQuickFix> fixes = ContainerUtil.newArrayListWithCapacity(MAX_SUGGESTIONS);
            for (HeaderNameMatch match : matches) {
              fixes.add(new HeaderNameSpellingQuickFix(header, match));
              if (fixes.size() == MAX_SUGGESTIONS) {
                break;
              }
            }
            holder.registerProblem(
              header.getNameElement(), ManifestBundle.message("inspection.header.message"),
              ProblemHighlightType.GENERIC_ERROR_OR_WARNING, fixes.toArray(new HeaderNameSpellingQuickFix[fixes.size()])
            );
          }
        }
      }
    };
  }

  private static class HeaderNameSpellingQuickFix implements LocalQuickFix {
    private final Header myHeader;
    private final String myNewName;

    private HeaderNameSpellingQuickFix(Header header, HeaderNameMatch match) {
      myHeader = header;
      myNewName = match.getHeaderName();
    }

    @NotNull
    @Override
    public String getName() {
      return ManifestBundle.message("inspection.header.fix", myNewName);
    }

    @NotNull
    @Override
    public String getFamilyName() {
      return ManifestBundle.message("inspection.group");
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      myHeader.setName(myNewName);
    }
  }
}
