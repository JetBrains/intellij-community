class Temp {

  interface Future<F> {}

  class Message {
  }

  interface Client<C extends Client, M> {
    <T> Future<T> request(M request);
  }

  interface MessageClient extends Client<MessageClient, Message> {
    Future<Message> request(Message request);
  }

  abstract class AbstractClient implements MessageClient {
  }

  class ConcreteClient extends AbstractClient {
    public Future<Message> request(Message request) {
      return null;
    }
  }
} 