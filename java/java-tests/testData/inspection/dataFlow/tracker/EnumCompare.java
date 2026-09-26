/*
Value is always false (code == ReturnStatus.NOT_FOUND; line#12)
  Condition 'code != ReturnStatus.NOT_FOUND' was deduced
    Values cannot be equal because ReturnStatus.SUCCESS.ordinal != ReturnStatus.NOT_FOUND.ordinal
      Left operand is 0 (code != ReturnStatus.SUCCESS; line#11)
      and right operand is 1 (code != ReturnStatus.SUCCESS; line#11)
    and condition 'code == ReturnStatus.SUCCESS' was checked before (code != ReturnStatus.SUCCESS; line#11)
 */
class SampleClazz {
  public void foo(ReturnStatus code) {
    if (code != ReturnStatus.SUCCESS) {
    } else if (<selection>code == ReturnStatus.NOT_FOUND</selection>) {
    }
  }

  enum ReturnStatus {
    SUCCESS, NOT_FOUND
  }
}