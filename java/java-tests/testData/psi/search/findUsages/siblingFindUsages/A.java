interface WatchNode {
  void setObsolete();
}

abstract class XValueContainerNode {
  public void setObsolete() {
  }
}

class WatchNodeImpl extends XValueContainerNode implements WatchNode {

}

class Use {
  void fff(XValueContainerNode node) {
    node.setObsolete();
  }
}