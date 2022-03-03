// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection;

import com.intellij.diagnostic.PluginException;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Base class for local inspections.
 */
public abstract class LocalInspectionTool extends InspectionProfileEntry {
  public static final LocalInspectionTool[] EMPTY_ARRAY = new LocalInspectionTool[0];

  private static final Logger LOG = Logger.getInstance(LocalInspectionTool.class);

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
  private static final Pattern COMPILED_VALID_ID_PATTERN = Pattern.compile(VALID_ID_PATTERN);

  public static boolean isValidID(@NotNull String id) {
    return !id.isEmpty() && COMPILED_VALID_ID_PATTERN.matcher(id).matches();
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
  @NonNls
  @NotNull
  public String getID() {
    if (myNameProvider instanceof LocalDefaultNameProvider) {
      String id = ((LocalDefaultNameProvider)myNameProvider).getDefaultID();
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
   * Override and return {@code true} if your inspection (unlike almost all others)
   * must be called for every element in the whole file for each change, whatever small it was.
   * <p>
   * For example, 'Field can be local' inspection can report the field declaration when reference to it was added inside method hundreds lines below.
   * Hence, this inspection must be rerun on every change.
   * <p>
   * Please note that re-scanning the whole file can take considerable time and thus seriously impact the responsiveness, so
   * beg please use this mechanism once in a blue moon.
   *
   * @return true if inspection should be called for every element.
   */
  public boolean runForWholeFile() {
    return false;
  }

  /**
   * Override to report problems at file level.
   *
   * @param file       to check.
   * @param manager    InspectionManager to ask for ProblemDescriptor's from.
   * @param isOnTheFly true if called during on the fly editor highlighting. Called from Inspect Code action otherwise.
   * @return {@code null} if no problems found or not applicable at file level.
   */
  public ProblemDescriptor @Nullable [] checkFile(@NotNull PsiFile file, @NotNull InspectionManager manager, boolean isOnTheFly) {
    return null;
  }

  /**
   * Override to provide your own inspection visitor, if you need to store additional state in the
   * LocalInspectionToolSession user data or get information about the inspection scope.
   * Created visitor must not be recursive (e.g. it must not inherit {@link PsiRecursiveElementVisitor})
   * since it will be fed with every element in the file anyway.
   * Visitor created must be thread-safe since it might be called on several elements concurrently.
   *
   * @param holder     where visitor will register problems found.
   * @param isOnTheFly true if inspection was run in non-batch mode
   * @param session    the session in the context of which the tool runs.
   * @return not-null visitor for this inspection.
   * @see PsiRecursiveVisitor
   */
  @NotNull
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly, @NotNull LocalInspectionToolSession session) {
    return buildVisitor(holder, isOnTheFly);
  }

  /**
   * Override to provide your own inspection visitor.
   * Created visitor must not be recursive (e.g. it must not inherit {@link PsiRecursiveElementVisitor})
   * since it will be fed with every element in the file anyway.
   * Visitor created must be thread-safe since it might be called on several elements concurrently.
   *
   * @param holder     where visitor will register problems found.
   * @param isOnTheFly true if inspection was run in non-batch mode
   * @return not-null visitor for this inspection.
   * @see PsiRecursiveVisitor
   */
  @NotNull
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new PsiElementVisitor() {
      @Override
      public void visitFile(@NotNull PsiFile file) {
        addDescriptors(checkFile(file, holder.getManager(), isOnTheFly));
      }

      private void addDescriptors(ProblemDescriptor[] descriptors) {
        if (descriptors != null) {
          for (ProblemDescriptor descriptor : descriptors) {
            if (descriptor != null) {
              holder.registerProblem(descriptor);
            }
            else {
              Class<?> inspectionToolClass = LocalInspectionTool.this.getClass();
              LOG.error(PluginException.createByClass("Array returned from checkFile() method of " + inspectionToolClass + " contains null element",
                                                      null, inspectionToolClass));
            }
          }
        }
      }
    };
  }

  /**
   * Returns problem container (e.g., method, class, file) that is used as inspection view tree node.
   *
   * Consider {@link com.intellij.codeInspection.lang.RefManagerExtension#getElementContainer(PsiElement)}
   * to override container element for any inspection for given language.
   *
   * @param psiElement: problem element
   * @return problem container element
   */
  @Nullable
  public PsiNamedElement getProblemElement(@NotNull PsiElement psiElement) {
    return psiElement.getContainingFile();
  }

  /**
   * Called before this inspection tool' visitor started processing any PSI elements the IDE wanted it to process in this session.
   * There are no guarantees about which thread it's called from or whether there is a read/write action it's called under.
   */
  public void inspectionStarted(@NotNull LocalInspectionToolSession session, boolean isOnTheFly) {}

  /**
   * Called when this inspection tool' visitor finished processing all PSI elements the IDE wanted it to process in this session.
   * There are no guarantees about which thread it's called from or whether there is a read/write action it's called under.
   */
  public void inspectionFinished(@NotNull LocalInspectionToolSession session, @NotNull ProblemsHolder problemsHolder) {
  }

  @NotNull
  public List<ProblemDescriptor> processFile(@NotNull PsiFile file, @NotNull InspectionManager manager) {
    return manager.defaultProcessFile(this, file);
  }
}
