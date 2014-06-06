public enum State {
  STATE1 {
    State s = <error descr="It is illegal to access static member 'STATE2' from enum constructor or instance initializer">STATE2</error>;
  },
  STATE2 {
    State s = <error descr="It is illegal to access static member 'STATE1' from enum constructor or instance initializer">STATE1</error>;
  }
}
