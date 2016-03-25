// "Replace with lambda" "false"
class ForwardReference {
  Runnable TREE_UPDATER;

  {
    TREE_UPDATER = new Runna<caret>ble() {
      @Override
      public void run() {
        myTree.toString();
      }
    };
  }

  Object myTree;
}