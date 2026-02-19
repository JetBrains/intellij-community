interface IMessage {
  class <caret>Topic {
  }
}

abstract class Message implements IMessage {
  public static void main(String[] args) {
    new Topic();
  }
}