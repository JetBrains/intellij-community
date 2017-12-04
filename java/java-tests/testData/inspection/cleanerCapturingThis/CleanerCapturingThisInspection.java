import java.lang.ref.Cleaner;

class Anonymous {
  int fileDescriptor;

  static void free(int descriptor) {}

  Cleaner.Cleanable cleanable = Cleaner.create().register(this, <warning descr="Runnable passed to register() captures 'this' reference that leads to memory leak">new Runnable() {
    @Override
    public void run() {
      System.out.println("adsad");
    }
  }</warning>);
}

class Inner {
  int fileDescriptor;

  static void free(int descriptor) {}

  Cleaner.Cleanable cleanable = Cleaner.create().register(this, <warning descr="Runnable passed to register() captures 'this' reference that leads to memory leak">new MyRunnable()</warning>);

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

  Cleaner.Cleanable cleanable = Cleaner.create().register(this, <warning descr="Runnable passed to register() captures 'this' reference that leads to memory leak">this::run</warning>);

  private void run() {
    System.out.println("adsad");
    free(fileDescriptor);
  }
}

class LambdaExprBodyInstanceMethod {
  int fileDescriptor;

  static void free(int descriptor) {}

  Cleaner.Cleanable cleanable = Cleaner.create().register(this, <warning descr="Runnable passed to register() captures 'this' reference that leads to memory leak">() -> run()</warning>);

  private void run() {
    System.out.println("adsad");
    free(fileDescriptor);
  }
}

class LambdaInstanceField {
  int fileDescriptor;

  Cleaner.Cleanable cleanable = Cleaner.create().register(this, <warning descr="Runnable passed to register() captures 'this' reference that leads to memory leak">() -> {
    System.out.println("adsad");
    fileDescriptor = 0;
  }</warning>);
}

class LambdaInstanceMethod {
  int fileDescriptor;

  static void free(int descriptor) {}

  Cleaner.Cleanable cleanable = Cleaner.create().register(this, <warning descr="Runnable passed to register() captures 'this' reference that leads to memory leak">() -> {
    System.out.println("adsad");
    free(fileDescriptor);
  }</warning>);
}

class Base {
  int fileDescriptor;
}

class LambdaInstanceSuperField extends Base {
  Cleaner.Cleanable cleanable = Cleaner.create().register(this, <warning descr="Runnable passed to register() captures 'this' reference that leads to memory leak">() -> {
    System.out.println("adsad");
    fileDescriptor = 0;
  }</warning>);
}

class LambdaThis {
  int fileDescriptor;

  static void free(int descriptor) {}

  Cleaner.Cleanable cleanable = Cleaner.create().register(this, <warning descr="Runnable passed to register() captures 'this' reference that leads to memory leak">() -> {
    LambdaThis o = this;
  }</warning>);
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

// Reference as tracking target

class StaticMethodFactory {
  static Cleaner cleaner = Cleaner.create();

  static ResourceHolder create() {
    ResourceHolder holder = new ResourceHolder();
    cleaner.register(holder, <warning descr="Runnable passed to register() captures 'holder' reference that leads to memory leak">holder::free</warning>);
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
    cleaner.register(holder, <warning descr="Runnable passed to register() captures 'holder' reference that leads to memory leak">() -> free(holder.resource)</warning>);
  }

  static void free(int resource){}
}


class InnerAccesInstanceOuterMembers {
  int resource;

  class Inner {
    Cleaner cleaner = Cleaner.create();

    public Inner() {
      cleaner.register(this, <warning descr="Runnable passed to register() captures 'this' reference that leads to memory leak">() -> resource = -1</warning>);
    }
  }
}