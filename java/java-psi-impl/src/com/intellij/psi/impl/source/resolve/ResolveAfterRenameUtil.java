// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source.resolve;

import com.intellij.openapi.util.Key;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.JavaResolveResult;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiImportStatementBase;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.ResolveState;
import com.intellij.psi.impl.light.LightField;
import com.intellij.psi.scope.JavaScopeProcessorEvent;
import com.intellij.psi.scope.NameHint;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.scope.util.PsiScopesUtil;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.concurrency.annotations.RequiresReadLock;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * What a short name at a place means once an element has that name, found before the element has it: the code as it is sees the element
 * under its old name only. The walk of the resolve offers the element under the new name, and the processor of Java decides what the name
 * means, in its order: the inner scopes first, a class with its supertypes, then the file with its own classes, its single imports, its
 * package and its imports on demand. Nothing is written.
 */
@ApiStatus.Internal
public final class ResolveAfterRenameUtil {
  private ResolveAfterRenameUtil() {
  }

  /**
   * What the new name means at a place. The examples rename the class {@code p.Old} to {@code New} and resolve {@code New} at the
   * reference {@code Old o} of {@code q/Use.java}.
   */
  public static final class Target {
    public enum Kind {
      /**
       * The element under its new name.
       * <pre>{@code
       * package q; import p.*; class Use { Old o; }
       * }</pre>
       */
      RENAMED,
      /**
       * Another element, which the resolve finds first, see {@link #element()} and {@link #fileContext()}. Here it is the member class
       * {@code Use.New}:
       * <pre>{@code
       * package q; import p.*; class Use { Old o; class New {} }
       * }</pre>
       */
      OTHER,
      /**
       * Several elements that no one of them hides, see {@link #elements()}. The renamed element may be one of them. Here they are
       * {@code p.Old} and {@code r.New}:
       * <pre>{@code
       * package q; import p.*; import r.*; class Use { Old o; }
       * }</pre>
       */
      AMBIGUOUS,
      /**
       * Nothing: the place does not see the element under the new name, and nothing else has the name there. Here the file has no import
       * of {@code p}:
       * <pre>{@code
       * package q; class Use { p.Old o; }
       * }</pre>
       */
      NONE
    }

    private static final Target NOTHING = new Target(Kind.NONE, Collections.emptyList(), null);

    private final @NotNull Kind myKind;
    private final @NotNull @Unmodifiable List<PsiElement> myElements;
    private final @Nullable PsiElement myFileContext;

    private Target(@NotNull Kind kind, @NotNull @Unmodifiable List<PsiElement> elements, @Nullable PsiElement fileContext) {
      myKind = kind;
      myElements = elements;
      myFileContext = fileContext;
    }

    public @NotNull Kind kind() {
      return myKind;
    }

    /** @return the other element of {@link Kind#OTHER}; null for another kind */
    public @Nullable PsiElement element() {
      return myKind == Kind.OTHER ? myElements.get(0) : null;
    }

    /** @return the elements of {@link Kind#AMBIGUOUS}, the renamed one among them; the one of {@link Kind#OTHER}; none for another kind */
    public @NotNull @Unmodifiable List<PsiElement> elements() {
      return myElements;
    }

    /** @return the import that gives the other element of {@link Kind#OTHER}; null if the place sees it without one */
    public @Nullable PsiElement fileContext() {
      return myFileContext;
    }
  }

  /**
   * The resolve offers {@code view} instead of the class to rename and instead of its renamed copy. It skips the imports that
   * {@link RenamedClassView#getIgnoredImports} holds: the rename deletes them. The rename of {@code pack1.MyList} to {@code List} deletes
   * the single import of {@code java.util.List}, so {@code List} at {@code MyList myList} means the renamed class:
   * <pre>{@code
   * package pack2;
   * import java.util.List;
   * import pack1.*;
   * class Usage { void m() { List list = null; MyList myList = null; } }
   * }</pre>
   * With no ignored import, the result is {@link Target.Kind#OTHER}, {@code java.util.List} of the single import. See {@link Target.Kind}
   * for the other results.
   *
   * @return what a short class reference of the new name of {@code view} at {@code place} resolves to after the rename
   */
  @RequiresReadLock
  public static @NotNull Target resolveClass(@NotNull RenamedClassView view, @NotNull PsiElement place) {
    String newName = view.getName();
    PsiJavaCodeReferenceElement reference = referenceAt(newName, place);
    if (reference == null) return Target.NOTHING;
    Collection<? extends PsiImportStatementBase> ignoredImports = view.getIgnoredImports();
    ClassResolverProcessor processor = new ClassResolverProcessor(newName, reference, reference.getContainingFile()) {
      private PsiElement myFileContext;

      @Override
      public void handleEvent(@NotNull PsiScopeProcessor.Event event, Object associated) {
        super.handleEvent(event, associated);
        if (event == JavaScopeProcessorEvent.SET_CURRENT_FILE_CONTEXT) {
          myFileContext = (PsiElement)associated;
        }
      }

      @Override
      public boolean execute(@NotNull PsiElement element, @NotNull ResolveState state) {
        if (myFileContext != null && ignoredImports.contains(myFileContext)) return true;
        return super.execute(view.isRenamed(element) ? view : element, state);
      }

      // The class to rename has its old name yet: without the hint the walk offers it too.
      @Override
      public <T> T getHint(@NotNull Key<T> hintKey) {
        return hintKey == NameHint.KEY ? null : super.getHint(hintKey);
      }
    };
    PsiScopesUtil.resolveAndWalk(processor, reference, null);
    return targetOf(processor.getResult(), view.getToRename(), view);
  }

  /**
   * @return what a short reference of {@code newName} at {@code place} resolves to as a variable once {@code toRename} has the name
   */
  @RequiresReadLock
  public static @NotNull Target resolveField(@NotNull PsiField toRename, @NotNull String newName, @NotNull PsiElement place) {
    PsiJavaCodeReferenceElement reference = referenceAt(newName, place);
    PsiClass containingClass = toRename.getContainingClass();
    if (reference == null || containingClass == null) return Target.NOTHING;
    PsiField renamedView = new LightField(toRename.getManager(), toRename, containingClass) {
      @Override
      public @NotNull String getName() {
        return newName;
      }
    };
    VariableResolverProcessor processor = new VariableResolverProcessor(reference, reference.getContainingFile()) {
      @Override
      public boolean execute(@NotNull PsiElement element, @NotNull ResolveState state) {
        return super.execute(element == toRename ? renamedView : element, state);
      }

      // The field to rename has its old name yet: without the hint the walk offers it too.
      @Override
      public <T> T getHint(@NotNull Key<T> hintKey) {
        return hintKey == NameHint.KEY ? null : super.getHint(hintKey);
      }
    };
    PsiScopesUtil.resolveAndWalk(processor, reference, null);
    return targetOf(processor.getResult(), toRename, renamedView);
  }

  /**
   * As a call of {@code newName} at {@code place} looks for its method, JLS 15.12.1: in the innermost enclosing class that has a method of
   * the name, its own or inherited. A class that inherits a method to rename has the name too.
   * <pre>{@code
   * class Base { void run() {} }
   * class Outer {
   *   void execute() {}
   *   class Inner extends Base { void f() { run(); } }
   * }
   * // rename Base.run to execute: at run() the result is Inner, not Outer
   * }</pre>
   *
   * @param toRename the methods that get the name
   * @return the innermost enclosing class of {@code place} that has a method of {@code newName} after the rename; null if there is none
   */
  @RequiresReadLock
  public static @Nullable PsiClass methodClass(@NotNull Collection<? extends PsiMethod> toRename,
                                               @NotNull String newName,
                                               @NotNull PsiElement place) {
    PsiClass aClass = PsiTreeUtil.getContextOfType(place, PsiClass.class, false);
    while (aClass != null && aClass.findMethodsByName(newName, true).length == 0 && !inheritsAny(aClass, toRename)) {
      aClass = PsiTreeUtil.getContextOfType(aClass, PsiClass.class, true);
    }
    return aClass;
  }

  private static boolean inheritsAny(@NotNull PsiClass aClass, @NotNull Collection<? extends PsiMethod> methods) {
    for (PsiMethod method : methods) {
      if (InheritanceUtil.isInheritorOrSelf(aClass, method.getContainingClass(), true)) return true;
    }
    return false;
  }

  private static @Nullable PsiJavaCodeReferenceElement referenceAt(@NotNull String name, @NotNull PsiElement place) {
    try {
      return JavaPsiFacade.getInstance(place.getProject()).getParserFacade().createReferenceFromText(name, place);
    }
    catch (IncorrectOperationException e) {
      return null;
    }
  }

  /**
   * @param renamedView the view of {@code toRename} under the new name, which the resolve offered instead of it
   * @return the target of {@code results}; in {@link Target#elements()} the view is {@code toRename} again
   */
  private static @NotNull Target targetOf(JavaResolveResult @NotNull [] results, @NotNull PsiElement toRename, @NotNull PsiElement renamedView) {
    if (results.length == 0) return Target.NOTHING;
    if (results.length > 1) {
      List<PsiElement> elements = ContainerUtil.map(results, result -> result.getElement() == renamedView ? toRename : result.getElement());
      return new Target(Target.Kind.AMBIGUOUS, elements, null);
    }
    JavaResolveResult result = results[0];
    PsiElement element = result.getElement();
    if (element == renamedView) return new Target(Target.Kind.RENAMED, Collections.emptyList(), null);
    if (element == null) return Target.NOTHING;
    return new Target(Target.Kind.OTHER, Collections.singletonList(element), result.getCurrentFileResolveScope());
  }
}
