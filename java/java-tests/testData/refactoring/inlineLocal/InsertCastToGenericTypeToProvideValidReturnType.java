
interface Vector<M> {
  M get(int i);
}
class Test {

  private static void call(Vector args_) {
    Vector<String> ar<caret>gs = args_;

    String s = args.get(0);
  }
}