// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection;

import com.intellij.diagnostic.PluginException;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.PossiblyDumbAware;
import com.intellij.psi.*;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Base class for local inspections.
 * <p/>
 * You can make your inspection dumb-aware by marking it with {@link com.intellij.openapi.project.DumbAware DumbAware} interface.
 * Such an inspection must not use indexes during its inference, or it must be prepared to catch
 * {@link com.intellij.openapi.project.IndexNotReadyException IndexNotReadyException}.
 * In this case, the inspection shall just silently catch it and not report any warnings.
 *
 * @see <a href="https://plugins.jetbrains.com/docs/intellij/code-inspections.html">Code Inspections (IntelliJ Platform Docs)</a>
 * @see GlobalInspectionTool
 */
public abstract class LocalInspectionTool extends InspectionProfileEntry implements PossiblyDumbAware {
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
  @Language("RegExp") public static final @NonNls String VALID_ID_PATTERN = "[a-zA-Z_0-9.-]+";
  private static final Pattern COMPILED_VALID_ID_PATTERN = Pattern.compile(VALID_ID_PATTERN);

  public static boolean isValidID(@NotNull String id) {
    return !id.isEmpty() && COMPILED_VALID_ID_PATTERN.matcher(id).matches();
  }

  /**
   * If you want to change the suppression ID, you have to define it in XML as well.
   *
   * <p>Inspection tool ID is a descriptive name to be used in "suppress" comments and annotations.
   * <p>It must satisfy {@link #VALID_ID_PATTERN} regexp pattern.
   * <p>If not defined {@link #getShortName()} is used as tool ID.
   *
   * @return inspection tool ID.
   */
  public @NonNls @NotNull String getID() {
    if (myNameProvider instanceof LocalDefaultNameProvider) {
      String id = ((LocalDefaultNameProvider)myNameProvider).getDefaultID();
      if (id != null) {
        return id;
      }
    }
    return getShortName();
  }

  @Override
  public final @NotNull String getSuppressId() {
    return getID();
  }

  @Override
  public @NonNls @Nullable String getAlternativeID() {
    if (myNameProvider instanceof LocalDefaultNameProvider) {
      return ((LocalDefaultNameProvider)myNameProvider).getDefaultAlternativeID();
    }
    return null;
  }

  /**
   * Override and return {@code true} if your inspection (unlike almost all others)
   * must be called for every element in the whole file for each change, whatever small it was.
   * <p>
   * For example, 'Field can be local' inspection should revisit the field declaration,
   * when the reference to it is added hundreds lines below, from inside some other method.
   * <p>
   * Please note that re-scanning the whole file can take considerable time and thus seriously impact the responsiveness, so
   * please return {@code true} from this method only when absolutely necessary.
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
   * Override to provide your own inspection visitor if you need to store additional state in the
   * {@link LocalInspectionToolSession} user data or get information about the inspection scope.
   * Created visitor must not be recursive (e.g., it must not inherit {@link PsiRecursiveElementVisitor})
   * since it will be fed with every element in the file anyway.
   * Visitor created must be thread-safe since it might be called on several elements concurrently.
   * If the inspection should not run in the given context return {@link PsiElementVisitor#EMPTY_VISITOR}
   *
   * @param holder     where the visitor will register problems it found.
   * @param isOnTheFly true if inspection was run in non-batch mode
   * @param session    the session in the context of which the tool runs.
   * @return not-null visitor for this inspection.
   * @see PsiRecursiveVisitor
   */
  public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly, @NotNull LocalInspectionToolSession session) {
    return buildVisitor(holder, isOnTheFly);
  }

  /**
   * @param file file to check
   * @return true if the inspection is available for this particular file. If false is returned, 
   * {@link #buildVisitor(ProblemsHolder, boolean, LocalInspectionToolSession)} method will not be called by inspection engine.
   */
  public boolean isAvailableForFile(@NotNull PsiFile file) {
    return true;
  }

  /**
   * Override to provide your own inspection visitor.
   * Created visitor must not be recursive (e.g., it must not inherit {@link PsiRecursiveElementVisitor})
   * since it will be fed with every element in the file anyway.
   * Visitor created must be thread-safe since it might be called on several elements concurrently.
   * If the inspection should not run in the given context return {@link PsiElementVisitor#EMPTY_VISITOR}
   *
   * @param holder     where the visitor will register problems it found.
   * @param isOnTheFly true if inspection was run in non-batch mode
   * @return not-null visitor for this inspection.
   * @see PsiRecursiveVisitor
   */
  public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new PsiFileElementVisitor(holder, isOnTheFly);
  }

  private final class PsiFileElementVisitor extends PsiElementVisitor implements HintedPsiElementVisitor {
    private final @NotNull ProblemsHolder myHolder;
    private final boolean myIsOnTheFly;

    private PsiFileElementVisitor(@NotNull ProblemsHolder holder, boolean fly) {
      this.myHolder = holder;
      this.myIsOnTheFly = fly;
    }

    @Override
    public void visitFile(@NotNull PsiFile file) {
      addDescriptors(checkFile(file, myHolder.getManager(), myIsOnTheFly));
    }

    private void addDescriptors(ProblemDescriptor @Nullable [] descriptors) {
      if (descriptors != null) {
        for (ProblemDescriptor descriptor : descriptors) {
          if (descriptor != null) {
            myHolder.registerProblem(descriptor);
          }
          else {
            Class<?> inspectionToolClass = LocalInspectionTool.this.getClass();
            LOG.error(PluginException.createByClass(
              "Array returned from checkFile() method of " + inspectionToolClass + " contains null element: " +
              Arrays.toString(descriptors),
              null, inspectionToolClass));
          }
        }
      }
    }

    @Override
    public @NotNull List<Class<?>> getHintPsiElements() {
      return List.of(PsiFile.class);
    }
  }

  /**
   * Returns problem container (e.g., method, class, file) that is used as inspection view tree node.
   * <p>
   * Consider {@link com.intellij.codeInspection.lang.RefManagerExtension#getElementContainer(PsiElement)}
   * to override the container element for any inspection for given language.
   *
   * @param psiElement: problem element
   * @return problem container element
   */
  public @Nullable PsiNamedElement getProblemElement(@NotNull PsiElement psiElement) {
    return psiElement.getContainingFile();
  }

  /**
   * Called before this inspection tool's visitor started processing any PSI elements the IDE wanted it to process in this session.
   * There are no guarantees about which thread it's called from or whether there is a read/write action it's called under.
   */
  public void inspectionStarted(@NotNull LocalInspectionToolSession session, boolean isOnTheFly) {}

  /**
   * Called when this inspection tool's visitor finished processing all the PSI elements the IDE wanted to process in this session.
   * There are no guarantees about which thread it's called from or whether there is a read/write action it's called under.
   */
  public void inspectionFinished(@NotNull LocalInspectionToolSession session, @NotNull ProblemsHolder problemsHolder) {
  }

  public @NotNull List<ProblemDescriptor> processFile(@NotNull PsiFile file, @NotNull InspectionManager manager) {
    return manager.defaultProcessFile(this, file);
  }
}
