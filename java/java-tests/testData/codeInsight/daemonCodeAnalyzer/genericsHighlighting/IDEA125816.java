public enum State {
  STATE1 {
    State s = <error descr="Accessing enum constant from enum instance field initializer is not allowed">STATE2</error>;
  },
  STATE2 {
    State s = <error descr="Accessing enum constant from enum instance field initializer is not allowed">STATE1</error>;
  }
}
