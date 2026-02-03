import java.lang.ref.Cleaner;

class Anonymous {
  int fileDescriptor;

  static void free(int descriptor) {}

  Cleaner.Cleanable cleanable = Cleaner.create().register(this, <warning descr="Runnable passed to Cleaner.register() captures 'this' reference">new Runnable() {
    @Override
    public void run() {
      System.out.println("adsad");
    }
  }</warning>);
}

class Inner {
  int fileDescriptor;

  static void free(int descriptor) {}

  Cleaner.Cleanable cleanable = Cleaner.create().register(this, new <warning descr="Runnable passed to Cleaner.register() captures 'this' reference">MyRunnable</warning>());

  private class MyRunnable implements Runnable {
    @Override
    public void run() {
      System.out.println("adsad");
    }
  }
}

class InstanceMethodReference {
  int fileDescriptor;

  static void free(int descriptor) {}

  Cleaner.Cleanable cleanable = Cleaner.create().register(this, <warning descr="Runnable passed to Cleaner.register() captures 'this' reference">this</warning>::run);

  private void run() {
    System.out.println("adsad");
    free(fileDescriptor);
  }
}

class LambdaExprBodyInstanceMethod {
  int fileDescriptor;

  static void free(int descriptor) {}

  Cleaner.Cleanable cleanable = Cleaner.create().register(this, () -> <warning descr="Runnable passed to Cleaner.register() captures 'this' reference">run</warning>());

  private void run() {
    System.out.println("adsad");
    free(fileDescriptor);
  }
}

class LambdaInstanceField {
  int fileDescriptor;

  Cleaner.Cleanable cleanable = Cleaner.create().register(this, () -> {
    System.out.println("adsad");
    <warning descr="Runnable passed to Cleaner.register() captures 'this' reference">fileDescriptor</warning> = 0;
  });
}

class LambdaInstanceMethod {
  int fileDescriptor;

  static void free(int descriptor) {}

  Cleaner.Cleanable cleanable = Cleaner.create().register(this, () -> {
    System.out.println("adsad");
    free(<warning descr="Runnable passed to Cleaner.register() captures 'this' reference">fileDescriptor</warning>);
  });
}

class Base {
  int fileDescriptor;
}

class LambdaInstanceSuperField extends Base {
  Cleaner.Cleanable cleanable = Cleaner.create().register(this, () -> {
    System.out.println("adsad");
    <warning descr="Runnable passed to Cleaner.register() captures 'this' reference">fileDescriptor</warning> = 0;
  });
}

class LambdaThis {
  int fileDescriptor;

  static void free(int descriptor) {}

  Cleaner.Cleanable cleanable = Cleaner.create().register(this, () -> {
    LambdaThis o = <warning descr="Runnable passed to Cleaner.register() captures 'this' reference">this</warning>;
  });
}

class StaticMethodReference {
  int fileDescriptor;

  static void free(int descriptor) {}

  Cleaner.Cleanable cleanable = Cleaner.create().register(this, StaticMethodReference::run);

  private static void run() {
    System.out.println("adsad");
  }
}

class ResourceHolder {
  int resource;
  public void free(){}
  public void free(int resource){}
}

class SafeInstanceMethodReference {
  Cleaner cleaner = Cleaner.create();

  SafeInstanceMethodReference() {
    ResourceHolder resource = new ResourceHolder();
    cleaner.register(this, resource::free);
  }
}


class FieldMethodReference {
  Cleaner cleaner = Cleaner.create();
  ResourceHolder resource = new ResourceHolder();

  FieldMethodReference() {
    cleaner.register(this, resource::free);
  }
}

// Reference as tracking target

class StaticMethodFactory {
  static Cleaner cleaner = Cleaner.create();

  static ResourceHolder create() {
    ResourceHolder holder = new ResourceHolder();
    cleaner.register(holder, <warning descr="Runnable passed to Cleaner.register() captures 'holder' reference">holder</warning>::free);
    return holder;
  }
}

class ConstructorDelegatesToStaticMethod {
  int resource;
  static Cleaner cleaner = Cleaner.create();

  ConstructorDelegatesToStaticMethod(int resource) {
    this.resource = resource;
    register(this);
  }

  static void register(ConstructorDelegatesToStaticMethod holder) {
    cleaner.register(holder, () -> free(<warning descr="Runnable passed to Cleaner.register() captures 'holder' reference">holder</warning>.resource));
  }

  static void free(int resource){}
}


class InnerAccesInstanceOuterMembers {
  int resource;

  class Inner {
    Cleaner cleaner = Cleaner.create();

    public Inner() {
      cleaner.register(this, () -> <warning descr="Runnable passed to Cleaner.register() captures 'this' reference">resource</warning> = -1);
    }
  }
}

class LambdaUsingAnotherInstanceMember {
  int fileDescriptor;

  static Cleaner cleaner = Cleaner.create();

  void register() {
    LambdaUsingAnotherInstanceMember another = new LambdaUsingAnotherInstanceMember();
    cleaner.register(this, () -> {
      another.fileDescriptor = 12;
    });
  }
}