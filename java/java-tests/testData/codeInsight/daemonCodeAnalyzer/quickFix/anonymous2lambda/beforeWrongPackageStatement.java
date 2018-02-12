// "Replace with lambda" "true"
package mismatch;

interface CanceledStatus {
  CanceledStatus NULL = new Canceled<caret>Status() {
    @Override
    public boolean isCanceled() {
      return false;
    }
  };

  boolean isCanceled();
}