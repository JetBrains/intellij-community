import java.lang.ref.Cleaner;

class CleanerCapturingThis {
  int fileDescriptor;

  Cleaner.Cleanable cleanable = Cleaner.create().register(this, <warning descr="Cleanable captures 'this' reference">() -> {
    System.out.println("adsad");
    fileDescriptor = 0;
  }</warning>);
}