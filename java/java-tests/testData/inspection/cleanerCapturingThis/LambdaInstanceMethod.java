import java.lang.ref.Cleaner;

class CleanerCapturingThis {
  int fileDescriptor;

  static void free(int descriptor) {}

  Cleaner.Cleanable cleanable = Cleaner.create().register(this, <warning descr="Cleanable captures 'this' reference">() -> {
    System.out.println("adsad");
    free(fileDescriptor);
  }</warning>);
}