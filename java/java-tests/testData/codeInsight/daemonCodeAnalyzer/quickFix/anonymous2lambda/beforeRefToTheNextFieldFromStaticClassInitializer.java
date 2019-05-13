// "Replace with lambda" "false"
class ForwardReference {
  static Runnable TREE_UPDATER;

  static {
    TREE_UPDATER = new Runna<caret>ble() {
      @Override
      public void run() {
        myTree.toString();
      }
    };
  }

  static Object myTree;
}