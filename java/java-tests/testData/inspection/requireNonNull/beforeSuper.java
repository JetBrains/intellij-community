// "Replace 'if' statement with 'Objects.requireNonNullElse()' call" "false"

class Component {
  public Component add(Component component, int index) {
    return new Component();
  }
}

class Usage extends Component {
  protected Component cont = new Usage();

  @Override
  public Component add(Component component, int index) {
    if<caret> (cont == null) {
      return (super.add(component, index));
    }
    return cont.add(component, index);
  }
}