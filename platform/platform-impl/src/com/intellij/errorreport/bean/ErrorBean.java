package com.intellij.errorreport.bean;

import com.intellij.util.SystemProperties;

import java.util.Date;

import org.jetbrains.annotations.NonNls;

/**
 * Created by IntelliJ IDEA.
 * User: stathik
 * Date: May 5, 2003
 * Time: 9:34:26 PM
 * To change this template use Options | File Templates.
 */
public class ErrorBean {
  private String notifierId;
  private String exceptionHashCode;
  private Date date;
  private String os;
  private String lastAction;
  private String description;

  public void autoInit () {
    os = SystemProperties.getOsName();
    date = new Date();
  }

  public String getNotifierId() {
    return notifierId;
  }

  public void setNotifierId(String notifierId) {
    this.notifierId = notifierId;
  }

  public Date getDate() {
    return date;
  }

  public void setDate(Date date) {
    this.date = date;
  }

  public String getOs() {
    return os;
  }

  public void setOs(String os) {
    this.os = os;
  }

  public String getLastAction() {
    return lastAction;
  }

  public void setLastAction(String lastAction) {
    this.lastAction = lastAction;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(@NonNls String description) {
    this.description = description;
  }

  public String getExceptionHashCode() {
    return exceptionHashCode;
  }

  public void setExceptionHashCode(String exceptionId) {
    exceptionHashCode = exceptionId;
  }
}
