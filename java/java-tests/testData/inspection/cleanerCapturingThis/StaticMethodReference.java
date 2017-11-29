import java.lang.ref.Cleaner;

class CleanerCapturingThis {
  int fileDescriptor;

  static void free(int descriptor) {}

  Cleaner.Cleanable cleanable = Cleaner.create().register(this, CleanerCapturingThis::run);

  private static void run() {
    System.out.println("adsad");
  }
}