package com.intellij.codeInspection.dataFlow;

import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.psi.*;
import com.siyeh.ig.psiutils.ExpressionUtils;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.PropertyKey;

import java.util.Objects;
import java.util.function.Consumer;

import static com.intellij.codeInspection.InspectionsBundle.BUNDLE;

/**
 * Represents a kind of nullability problem
 * @param <T> a type of anchor element which could be associated with given nullability problem kind
 */
public class NullabilityProblemKind<T extends PsiElement> {
  private final String myName;
  private final String myNullLiteralMessage;
  private final String myNormalMessage;

  private NullabilityProblemKind(@NotNull String name) {
    myName = name;
    myNullLiteralMessage = null;
    myNormalMessage = null;
  }

  private NullabilityProblemKind(@NotNull String name, @NotNull @PropertyKey(resourceBundle = BUNDLE) String message) {
    this(name, message, message);
  }

  private NullabilityProblemKind(@NotNull String name,
                                 @NotNull @PropertyKey(resourceBundle = BUNDLE) String nullLiteralMessage,
                                 @NotNull @PropertyKey(resourceBundle = BUNDLE) String normalMessage) {
    myName = name;
    myNullLiteralMessage = InspectionsBundle.message(nullLiteralMessage);
    myNormalMessage = InspectionsBundle.message(normalMessage);
  }

  public static final NullabilityProblemKind<PsiMethodCallExpression> callNPE = new NullabilityProblemKind<>("callNPE");
  public static final NullabilityProblemKind<PsiMethodReferenceExpression> callMethodRefNPE =
    new NullabilityProblemKind<>("callMethodRefNPE", "dataflow.message.npe.methodref.invocation");
  public static final NullabilityProblemKind<PsiNewExpression> innerClassNPE =
    new NullabilityProblemKind<>("innerClassNPE", "dataflow.message.npe.inner.class.construction");
  public static final NullabilityProblemKind<PsiExpression> fieldAccessNPE =
    new NullabilityProblemKind<>("fieldAccessNPE", "dataflow.message.npe.field.access.sure", "dataflow.message.npe.field.access");
  public static final NullabilityProblemKind<PsiArrayAccessExpression> arrayAccessNPE =
    new NullabilityProblemKind<>("arrayAccessNPE", "dataflow.message.npe.array.access");
  public static final NullabilityProblemKind<PsiElement> unboxingNullable =
    new NullabilityProblemKind<>("unboxingNullable", "dataflow.message.unboxing");
  public static final NullabilityProblemKind<PsiExpression> assigningToNotNull =
    new NullabilityProblemKind<>("assigningToNotNull", "dataflow.message.assigning.null", "dataflow.message.assigning.nullable");
  public static final NullabilityProblemKind<PsiExpression> storingToNotNullArray =
    new NullabilityProblemKind<>("storingToNotNullArray", "dataflow.message.storing.array.null", "dataflow.message.storing.array.nullable");
  public static final NullabilityProblemKind<PsiExpression> nullableReturn = new NullabilityProblemKind<>("nullableReturn");
  public static final NullabilityProblemKind<PsiExpression> nullableFunctionReturn =
    new NullabilityProblemKind<>("nullableFunctionReturn", "dataflow.message.return.nullable.from.notnull.function",
                                 "dataflow.message.return.nullable.from.notnull.function");
  public static final NullabilityProblemKind<PsiElement> passingNullableToNotNullParameter =
    new NullabilityProblemKind<>("passingNullableToNotNullParameter");
  public static final NullabilityProblemKind<PsiElement> passingNullableArgumentToNonAnnotatedParameter =
    new NullabilityProblemKind<>("passingNullableArgumentToNonAnnotatedParameter");

  /**
   * Creates a new {@link NullabilityProblem} of this kind using given anchor
   * @param anchor anchor to bind the problem to
   * @return newly created problem or null if anchor is null
   */
  @Contract("null -> null; !null -> !null")
  public final NullabilityProblem<T> problem(@Nullable T anchor) {
    return anchor == null ? null : new NullabilityProblem<>(this, anchor);
  }

  /**
   * Returns the supplied problem with adjusted type parameter or null if supplied problem kind is not this kind
   *
   * @param problem problem to check
   * @return the supplied problem or null
   */
  @SuppressWarnings("unchecked")
  @Nullable
  public final NullabilityProblem<T> asMyProblem(NullabilityProblem<?> problem) {
    return problem != null && problem.myKind == this ? (NullabilityProblem<T>)problem : null;
  }

  /**
   * Returns true if the kind of supplied problem is the same as this kind
   *
   * @param problem problem to check
   * @return true if the kind of supplied problem is the same as this kind
   */
  public final boolean isMyProblem(@Nullable NullabilityProblem<?> problem) {
    return problem != null && problem.myKind == this;
  }

  /**
   * Executes given consumer if the supplied problem has the same kind as this kind
   *
   * @param problem a problem to check
   * @param consumer a consumer to execute. A problem anchor is supplied as the consumer argument.
   */
  public void ifMyProblem(NullabilityProblem<?> problem, Consumer<T> consumer) {
    NullabilityProblem<T> myProblem = asMyProblem(problem);
    if (myProblem != null) {
      consumer.accept(myProblem.getAnchor());
    }
  }

  @Override
  public String toString() {
    return myName;
  }

  /**
   * Represents a concrete nullability problem on PSI which consists of PSI element (anchor) and {@link NullabilityProblemKind}.
   * @param <T> a type of anchor element
   */
  public static final class NullabilityProblem<T extends PsiElement> {
    private final @NotNull NullabilityProblemKind<T> myKind;
    private final @NotNull T myAnchor;

    NullabilityProblem(@NotNull NullabilityProblemKind<T> kind, @NotNull T anchor) {
      myKind = kind;
      myAnchor = anchor;
    }

    @NotNull
    public T getAnchor() {
      return myAnchor;
    }

    @NotNull
    public String getMessage() {
      if (myKind.myNullLiteralMessage == null || myKind.myNormalMessage == null) {
        throw new IllegalStateException("This problem kind has no message associated: " + myKind);
      }
      return myAnchor instanceof PsiExpression && ExpressionUtils.isNullLiteral((PsiExpression)myAnchor)
             ? myKind.myNullLiteralMessage
             : myKind.myNormalMessage;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof NullabilityProblem)) return false;
      NullabilityProblem<?> problem = (NullabilityProblem<?>)o;
      return myKind.equals(problem.myKind) && myAnchor.equals(problem.myAnchor);
    }

    @Override
    public int hashCode() {
      return Objects.hash(myKind, myAnchor);
    }

    @Override
    public String toString() {
      return "[" + myKind + "] " + myAnchor.getText();
    }
  }
}
