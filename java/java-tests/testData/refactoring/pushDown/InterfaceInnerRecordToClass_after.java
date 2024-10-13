interface IMessage {
}

abstract class Message implements IMessage {
  public static void main(String[] args) {
    new Topic();
  }

    public record Topic() {
    }
}