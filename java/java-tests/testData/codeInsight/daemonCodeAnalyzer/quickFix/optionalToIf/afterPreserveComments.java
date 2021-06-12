// "Fix all ''Optional' can be replaced with sequence of 'if' statements' problems in file" "true"

import java.util.*;

class Test {

  private LicenseManager ourInstance = null;

  LicenseManager setInstance(LicenseManager instance) {
    LicenseManager old = this.ourInstance;
    this.ourInstance = instance;
    return old;
  }

  private static interface LicenseManager {
  }

  private static class IdeaLicenseManager implements LicenseManager {
  }

  public LicenseManager getInstance() {
    final LicenseManager instance = ourInstance;
      /*1*/
      /*2*/
      /*3*/
      /*4*/
      LicenseManager result = null;
      if (instance != null) result = instance;
      if (result == null) result = setInstance(new IdeaLicenseManager(/*3*/));
      return result;
  }

}