package org.jetbrains.jps.api;

/**
* @author Eugene Zhuravlev
*         Date: 1/13/12
*/
public interface CanceledStatus {
  CanceledStatus NULL = new CanceledStatus() {
    @Override
    public boolean isCanceled() {
      return false;
    }
  };

  boolean isCanceled();
}
