package org.jetbrains.jps

/**
 * @author max
 */
class LazyInitializeableObject {
  def setInitializer(Closure init) {
    def meta = ProxyMetaClass.getInstance(getClass())
    meta.setInterceptor(new Intializing(initializer: init))
    setMetaClass(meta)
  }
}

private class Intializing implements PropertyAccessInterceptor {
  private Closure initializer

  Object beforeInvoke(Object object, String methodName, Object[] arguments) {
    init()
  }

  Object afterInvoke(Object object, String methodName, Object[] arguments, Object result) {
    return result
  }

  Object beforeGet(Object object, String property) {
    init()
  }

  void beforeSet(Object object, String property, Object newValue) {
    init()
  }

  boolean doInvoke() {
    true
  }

  def init() {
    if (initializer == null) return
    def i = initializer
    initializer = null
    i.call()
  }
}
