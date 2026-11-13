import java.lang.Record;
import java.lang.String;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.NullUnmarked;
import org.jspecify.annotations.Nullable;


@NullMarked
class GetSpecified {

  /// No inferred null, non-null return type
  int field = 0;

  /// Inferred NullMarked, since `nonNullField` is affected by NullMarked
  String nonNullField;

  /// No inferred null, non-null return type
  @Nullable String nullableField = "nullableField";

  /// No inferred null, non-nullness of return type implied by constructor
  NullabilityLover(){
    nonNullField = "nonNullField";
  }

  /// Inferred NullMarked, since `notAnnotatedParam` is affected by NullMarked
  NullabilityLover(Object notAnnotatedParam){
    nonNullField = notAnnotatedParam.toString();
  }

  /// No inferred null, no return type
  void foo(){}

  /// No inferred null, non-null return type
  boolean fiz(){ return false; }

  /// Inferred NullMarked, return type is affected
  String baz(){ return "baz"; }

  /// No inferred null, since return type is annotated
  @Nullable String biz(){ return null; }

  /// No inferred null, no nullable types
  void boop(int p0, int p1){}

  /// Inferred NullMarked, at least one non-annotated argument
  void baap(@Nullable String p0, String p1) {}

  /// No inferred null, all arguments are annotated
  void berp(@Nullable String p0, @NonNull String p1) {}

  /// No inferred null, no affected components. Is there a point to empty records ?
  record EmptyRecord(){}

  /// Inferred NullMarked, at least one non-annotated argument
  record AffectedRecord(@Nullable String component0, String component1){
    private static void componentAccess() {
      // No inferred null, the component is annotated nullable
      new AffectedRecord(null, "non-null").component0();

      // Inferred NullMarked, the component is under the effect of NullMarked
      new AffectedRecord(null, "nullable").component1();
    }
  }

  /// No inferred null, all arguments are annotated
  record AnnotatedRecord(@Nullable String component0, @Nullable String component1){}
}