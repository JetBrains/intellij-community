/*
Value is always false (code == ReturnStatus.NOT_FOUND; line#8)
  It's known that 'code != ReturnStatus.NOT_FOUND' from line #7 (code != ReturnStatus.SUCCESS; line#7)
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