import org.jetbrains.annotations.Contract;

class Zoo {
  @Contract(<warning descr="Contract return value 'null': not applicable for constructor">"null->null"</warning> )
  Zoo(Object o) {}

  @Contract("_->fail" )
  <warning descr="Contract clause '_ -> fail' is violated: no exception is thrown">Zoo</warning>(int o) {}

  @Contract("true->fail" )
  Zoo(boolean o) {
    if (o) throw new RuntimeException();
  }

}