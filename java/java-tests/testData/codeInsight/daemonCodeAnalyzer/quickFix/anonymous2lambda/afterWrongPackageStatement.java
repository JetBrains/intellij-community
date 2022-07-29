// "Replace with lambda" "true-preview"
package mismatch;

interface CanceledStatus {
  CanceledStatus NULL = () -> false;

  boolean isCanceled();
}