class DefaultObjectIdentifier {
  String user;
  String requestorClass;
  public boolean equals(Object otherObject) {
    if (otherObject == null || !(otherObject instanceof DefaultObjectIdentifier))
      return false;
    else {
      DefaultObjectIdentifier identifier = (DefaultObjectIdentifier) otherObject;
      if (user == null && identifier.user != null)
        return false;
      else if (requestorClass == null && identifier.requestorClass != null)
        return false;
      else if (((user == null && <warning descr="Condition 'identifier.user == null' is always 'true' when reached">identifier.user == null</warning>)
      || (this.user.equals(identifier.user)))
      &&
      ((requestorClass == null && <warning descr="Condition 'identifier.requestorClass == null' is always 'true' when reached">identifier.requestorClass == null</warning>)
      || this.requestorClass.equals(identifier.requestorClass)))
      return true;
      else
      return false;
    }
  }}