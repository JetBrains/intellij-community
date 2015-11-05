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
package com.intellij.codeInspection;

import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import org.intellij.lang.annotations.Language;
import org.intellij.lang.annotations.Pattern;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author max
 */
public abstract class LocalInspectionTool extends InspectionProfileEntry {
  public static final LocalInspectionTool[] EMPTY_ARRAY = new LocalInspectionTool[0];

  private static final Logger LOG = Logger.getInstance("#" + LocalInspectionTool.class.getName());

  interface LocalDefaultNameProvider extends DefaultNameProvider {
    @Nullable
    String getDefaultID();

    @Nullable
    String getDefaultAlternativeID();
  }

  /**
   * Pattern used for inspection ID validation.
   */
  @NonNls @Language("RegExp")
  public static final String VALID_ID_PATTERN = "[a-zA-Z_0-9.-]+";

  public static boolean isValidID(@NotNull String id) {
    return !id.isEmpty() && id.matches(VALID_ID_PATTERN);
  }

  /**
   * If you want to change suppression id you have to define it in XML as well.
   * 
   * <p>Inspection tool ID is a descriptive name to be used in "suppress" comments and annotations.
   * <p>It must satisfy {@link #VALID_ID_PATTERN} regexp pattern.
   * <p>If not defined {@link #getShortName()} is used as tool ID.
   *
   * @return inspection tool ID.
   */
  @Pattern(VALID_ID_PATTERN)
  @NonNls
  @NotNull
  public String getID() {
    if (myNameProvider instanceof LocalDefaultNameProvider) {
      final String id = ((LocalDefaultNameProvider)myNameProvider).getDefaultID();
      if (id != null) {
        return id;
      }
    }
    return getShortName();
  }

  @NotNull
  @Override
  protected final String getSuppressId() {
    return getID();
  }

  @Override
  @NonNls
  @Nullable
  public String getAlternativeID() {
    if (myNameProvider instanceof LocalDefaultNameProvider) {
      return ((LocalDefaultNameProvider)myNameProvider).getDefaultAlternativeID();
    }
    return null;
  }

  /**
   * Override this method and return true if your inspection (unlike almost all others)
   * must be called for every element in the whole file for each change, whatever small it was.
   * <p/>
   * For example, 'Field can be local' inspection can report the field declaration when reference to it was added inside method hundreds lines below.
   * Hence, this inspection must be rerun on every change.
   * <p/>
   * Please note that re-scanning the whole file can take considerable time and thus seriously impact the responsiveness, so
   * beg please use this mechanism once in a blue moon.
   *
   * @return true if inspection should be called for every element.
   */
  public boolean runForWholeFile() {
    return false;
  }

  /**
   * Override this to report problems at file level.
   *
   * @param file       to check.
   * @param manager    InspectionManager to ask for ProblemDescriptor's from.
   * @param isOnTheFly true if called during on the fly editor highlighting. Called from Inspect Code action otherwise.
   * @return <code>null</code> if no problems found or not applicable at file level.
   */
  @Nullable
  public ProblemDescriptor[] checkFile(@NotNull PsiFile file, @NotNull InspectionManager manager, boolean isOnTheFly) {
    return null;
  }

  /**
   * Override the method to provide your own inspection visitor, if you need to store additional state in the
   * LocalInspectionToolSession user data or get information about the inspection scope.
   * Visitor created must not be recursive (e.g. it must not inherit {@link PsiRecursiveElementVisitor})
   * since it will be fed with every element in the file anyway.
   * Visitor created must be thread-safe since it might be called on several elements concurrently.
   *
   * @param holder     where visitor will register problems found.
   * @param isOnTheFly true if inspection was run in non-batch mode
   * @param session    the session in the context of which the tool runs.
   * @return not-null visitor for this inspection.
   */
  @NotNull
  public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, final boolean isOnTheFly, @NotNull LocalInspectionToolSession session) {
    return buildVisitor(holder, isOnTheFly);
  }

  /**
   * Override the method to provide your own inspection visitor.
   * Visitor created must not be recursive (e.g. it must not inherit {@link PsiRecursiveElementVisitor})
   * since it will be fed with every element in the file anyway.
   * Visitor created must be thread-safe since it might be called on several elements concurrently.
   *
   * @param holder     where visitor will register problems found.
   * @param isOnTheFly true if inspection was run in non-batch mode
   * @return not-null visitor for this inspection.
   */
  @NotNull
  public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, final boolean isOnTheFly) {
    return new PsiElementVisitor() {
      @Override
      public void visitFile(PsiFile file) {
        addDescriptors(checkFile(file, holder.getManager(), isOnTheFly));
      }

      private void addDescriptors(final ProblemDescriptor[] descriptors) {
        if (descriptors != null) {
          for (ProblemDescriptor descriptor : descriptors) {
            LOG.assertTrue(descriptor != null, LocalInspectionTool.this.getClass().getName());
            holder.registerProblem(descriptor);
          }
        }
      }
    };
  }

  @Nullable
  public PsiNamedElement getProblemElement(PsiElement psiElement) {
    while (psiElement!=null && !(psiElement instanceof PsiFile)) {
      psiElement = psiElement.getParent();
    }
    return (PsiFile)psiElement;
  }

  public void inspectionStarted(@NotNull LocalInspectionToolSession session, boolean isOnTheFly) {}

  public void inspectionFinished(@NotNull LocalInspectionToolSession session, @NotNull ProblemsHolder problemsHolder) {
    inspectionFinished(session);
  }

  @Deprecated()
  public void inspectionFinished(@NotNull LocalInspectionToolSession session) {}
  @NotNull
  public List<ProblemDescriptor> processFile(@NotNull PsiFile file, @NotNull InspectionManager manager) {
    final ProblemsHolder holder = new ProblemsHolder(manager, file, false);
    LocalInspectionToolSession session = new LocalInspectionToolSession(file, 0, file.getTextLength());
    final PsiElementVisitor customVisitor = buildVisitor(holder, false, session);
    LOG.assertTrue(!(customVisitor instanceof PsiRecursiveElementVisitor),
                   "The visitor returned from LocalInspectionTool.buildVisitor() must not be recursive");

    inspectionStarted(session, false);

    final InjectedLanguageManager injectedLanguageManager = InjectedLanguageManager.getInstance(holder.getProject());
    file.accept(new PsiRecursiveElementWalkingVisitor() {
      @Override
      public void visitElement(PsiElement element) {
        element.accept(customVisitor);
        processInjectedFile(element);

        super.visitElement(element);
      }

      private void processInjectedFile(PsiElement element) {
        if (element instanceof PsiLanguageInjectionHost) {
          final List<Pair<PsiElement, TextRange>> files = injectedLanguageManager.getInjectedPsiFiles(element);
          if (files != null) {
            for (Pair<PsiElement, TextRange> pair : files) {
              pair.first.accept(new PsiRecursiveElementWalkingVisitor() {
                @Override
                public void visitElement(PsiElement injectedElement) {
                  injectedElement.accept(customVisitor);
                  super.visitElement(injectedElement);
                }
              });
            }
          }
        }
      }
    });

    inspectionFinished(session, holder);

    return holder.getResults();
  }
}
