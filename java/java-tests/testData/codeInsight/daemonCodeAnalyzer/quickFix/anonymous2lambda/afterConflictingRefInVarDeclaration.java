// "Replace with lambda" "true"
class Test {
  static class SetSubThreadReadPacket {
    private String getPostId() {
      return null;
    }
  }
  public void processPacket(String p) {
    IPacketProcessor<SetSubThreadReadPacket> iPacketProcessor = p1 -> {
      String root = p1.getPostId();
    };
  }

  private interface IPacketProcessor<T> {
    void process(SetSubThreadReadPacket p);
  }
}
