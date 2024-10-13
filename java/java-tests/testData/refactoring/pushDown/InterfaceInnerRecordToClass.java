interface IMessage {
  record <caret>Topic() {
  }
}

abstract class Message implements IMessage {
  public static void main(String[] args) {
    new Topic();
  }
}