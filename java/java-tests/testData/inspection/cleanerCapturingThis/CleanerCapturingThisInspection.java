import java.lang.ref.Cleaner;

class Anonymous {
  int fileDescriptor;

  static void free(int descriptor) {}

  Cleaner.Cleanable cleanable = Cleaner.create().register(this, <warning descr="Cleanable capturing 'this' reference leads to memory leak">new Runnable() {
    @Override
    public void run() {
      System.out.println("adsad");
    }
  }</warning>);
}

class Inner {
  int fileDescriptor;

  static void free(int descriptor) {}

  Cleaner.Cleanable cleanable = Cleaner.create().register(this, <warning descr="Cleanable capturing 'this' reference leads to memory leak">new MyRunnable()</warning>);

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

  Cleaner.Cleanable cleanable = Cleaner.create().register(this, <warning descr="Cleanable capturing 'this' reference leads to memory leak">this::run</warning>);

  private void run() {
    System.out.println("adsad");
    free(fileDescriptor);
  }
}

class LambdaExprBodyInstanceMethod {
  int fileDescriptor;

  static void free(int descriptor) {}

  Cleaner.Cleanable cleanable = Cleaner.create().register(this, <warning descr="Cleanable capturing 'this' reference leads to memory leak">() -> run()</warning>);

  private void run() {
    System.out.println("adsad");
    free(fileDescriptor);
  }
}

class LambdaInstanceField {
  int fileDescriptor;

  Cleaner.Cleanable cleanable = Cleaner.create().register(this, <warning descr="Cleanable capturing 'this' reference leads to memory leak">() -> {
    System.out.println("adsad");
    fileDescriptor = 0;
  }</warning>);
}

class LambdaInstanceMethod {
  int fileDescriptor;

  static void free(int descriptor) {}

  Cleaner.Cleanable cleanable = Cleaner.create().register(this, <warning descr="Cleanable capturing 'this' reference leads to memory leak">() -> {
    System.out.println("adsad");
    free(fileDescriptor);
  }</warning>);
}

class Base {
  int fileDescriptor;
}

class LambdaInstanceSuperField extends Base {
  Cleaner.Cleanable cleanable = Cleaner.create().register(this, <warning descr="Cleanable capturing 'this' reference leads to memory leak">() -> {
    System.out.println("adsad");
    fileDescriptor = 0;
  }</warning>);
}

class LambdaThis {
  int fileDescriptor;

  static void free(int descriptor) {}

  Cleaner.Cleanable cleanable = Cleaner.create().register(this, <warning descr="Cleanable capturing 'this' reference leads to memory leak">() -> {
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