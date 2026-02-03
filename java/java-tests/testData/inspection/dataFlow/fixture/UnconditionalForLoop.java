import java.util.HashMap;
class A {
  private HashMap<String, Integer> mLoaderIds = new HashMap<String, Integer>();

  public int getLoaderId(final String loaderName) {
    Integer loaderId = null;
    for (int i = 0; ; i++) {
      if (!mLoaderIds.containsValue(i)) {
        loaderId = i;
        mLoaderIds.put(loaderName, loaderId);
        break;
      }
    }


    return loaderId; // here IDEA says that "unboxing may produce NPE".
  }
}