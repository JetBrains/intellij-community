// "Replace with lambda" "true-preview"
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