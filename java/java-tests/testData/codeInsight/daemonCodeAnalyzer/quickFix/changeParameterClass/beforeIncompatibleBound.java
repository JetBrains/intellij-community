// "Make 'Descriptor' extend 'Descriptor'" "false"
public class ProposeToExtend {
  public static <T> void send(Descriptor<T> descriptor) {
    sendImpl(<caret>descriptor, null);
  }

  private static <T extends CharSequence> void sendImpl(Descriptor<T> descriptor, T value) {
  }
}

class Descriptor<T extends CharSequence> {
}