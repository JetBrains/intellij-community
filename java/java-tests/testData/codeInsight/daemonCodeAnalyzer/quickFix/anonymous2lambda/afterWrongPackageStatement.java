// "Replace with lambda" "true"
package mismatch;

interface CanceledStatus {
  CanceledStatus NULL = () -> false;

  boolean isCanceled();
}