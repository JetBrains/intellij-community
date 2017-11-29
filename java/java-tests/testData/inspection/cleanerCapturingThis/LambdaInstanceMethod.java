import java.lang.ref.Cleaner;

class CleanerCapturingThis {
  int fileDescriptor;

  static void free(int descriptor) {}

  Cleaner.Cleanable cleanable = Cleaner.create().register(this, <warning descr="Cleanable capturing 'this' reference leads to memory leak">() -> {
    System.out.println("adsad");
    free(fileDescriptor);
  }</warning>);
}