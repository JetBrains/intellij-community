import java.lang.ref.Cleaner;

class CleanerCapturingThis {
  int fileDescriptor;

  static void free(int descriptor) {}

  Cleaner.Cleanable cleanable = Cleaner.create().register(this, <warning descr="Cleanable capturing 'this' reference leads to memory leak">this::run</warning>);

  private void run() {
    System.out.println("adsad");
    free(fileDescriptor);
  }
}