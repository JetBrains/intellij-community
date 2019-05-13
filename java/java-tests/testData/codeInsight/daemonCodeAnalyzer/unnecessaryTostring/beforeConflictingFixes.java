// "Fix all 'Unnecessary call to 'toString()'' problems in file" "true"
public class MyFile {
  interface UUID {}
  interface InetAddress {}

  void test(UUID uuid, InetAddress address) {
    // IDEA-126310
    String nodeString = uuid.toStr<caret>ing() + address.toString();
  }
}
