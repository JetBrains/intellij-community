// "Replace with lambda" "true"
class Test {
  static class SetSubThreadReadPacket {
    private String getPostId() {
      return null;
    }
  }
  public void processPacket(String p) {
    IPacketProcessor<SetSubThreadReadPacket> iPacketProcessor = new IPacketProcessor<SetSubThrea<caret>dReadPacket>() {
      public void process(SetSubThreadReadPacket p) {
        String root = p.getPostId();
      }
    };
  }

  private interface IPacketProcessor<T> {
    void process(SetSubThreadReadPacket p);
  }
}
