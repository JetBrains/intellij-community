package ppp;

public class PublicClass {
  private int privateField;
  int packageLocalField;
  protected int protectedField;
  public int publicField;

  private void privateMethod() {
  }
  void packageLocalMethod() {
  }
  protected void protectedMethod() {
  }
  public void publicMethod() {
  }

  public Runnable anonymousClass() {
    return new Runnable() {
      public void run() {
      }
    };
  }

  public Object localClass() {
    class LocalClass {
    }
    return new LocalClass();
  }

  private interface PrivateHolder {
    final class PublicNested {
      private int hidden;
    }

    private static void hidden() {
    }
  }

  public enum SampleEnum {
    PLAIN,
    BODY {
      void body() {
      }
    };

    private int weight;
  }

  public static class Bridge extends PrivateBase {
    private int hidden;
  }

  public static class Q implements PrivateHolder {
    private int hidden;
  }

  static PrivateReturn leak() {
    return null;
  }

  private static class PrivateBase {
  }

  private static class PrivateReturn {
  }


  protected static class ProtectedClass {
    private int privateField;
    int packageLocalField;
    protected int protectedField;
    public int publicField;

    private void privateMethod() {
    }
    void packageLocalMethod() {
    }
    protected void protectedMethod() {
    }
    public void publicMethod() {
    }
  }

  private static class PrivateClass {
    private int privateField;
    int packageLocalField;
    protected int protectedField;
    public int publicField;

    private void privateMethod() {
    }
    void packageLocalMethod() {
    }
    protected void protectedMethod() {
    }
    public void publicMethod() {
    }
  }

}

class PackageLocalClass {
  private int privateField;
  int packageLocalField;
  protected int protectedField;
  public int publicField;

  private void privateMethod() {
  }
  void packageLocalMethod() {
  }
  protected void protectedMethod() {
  }
  public void publicMethod() {
  }
}

