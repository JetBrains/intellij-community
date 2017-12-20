package com.intellij.execution.jshell.protocol;

import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlEnum;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Eugene Zhuravlev
 */
@XmlRootElement
public class Request extends Message{
  private Command myCommand;
  private String myCodeText;
  private List<String> myClassPath;

  @XmlEnum
  public enum Command{
    EVAL, DROP_STATE, EXIT
  }

  public Request() {
  }

  public Request(String uid, Command cmd, String codeText) {
    super(uid);
    myCommand = cmd;
    myCodeText = codeText;
  }

  public Command getCommand() {
    return myCommand;
  }

  @XmlElement
  public void setCommand(Command command) {
    myCommand = command;
  }

  public String getCodeText() {
    return myCodeText;
  }

  @XmlElement
  public void setCodeText(String codeText) {
    myCodeText = codeText;
  }

  public List<String> getClassPath() {
    return myClassPath;
  }

  @XmlElement(name = "cp")
  public void setClassPath(List<String> classPath) {
    myClassPath = classPath;
  }

  public void addClasspathItem(String path) {
    List<String> cp = myClassPath;
    if (cp == null) {
      cp = new ArrayList<>();
      myClassPath = cp;
    }
    cp.add(path);
  }
}
