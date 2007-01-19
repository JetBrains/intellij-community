package com.intellij.openapi.components;

import com.intellij.openapi.extensions.ReaderConfigurator;
import com.thoughtworks.xstream.XStream;

public class ServiceDescriptor implements ReaderConfigurator {
  private String serviceInterface;
  private String serviceImplementation;

  public ServiceDescriptor() {
  }

  public ServiceDescriptor(final String serviceInterface, final String serviceImplementation) {
    this.serviceInterface = serviceInterface;
    this.serviceImplementation = serviceImplementation;
  }

  public String getServiceInterface() {
    return serviceInterface;
  }

  public String getServiceImplementation() {
    return serviceImplementation;
  }


  public void setServiceInterface(final String serviceInterface) {
    this.serviceInterface = serviceInterface;
  }

  public void setServiceImplementation(final String serviceImplementation) {
    this.serviceImplementation = serviceImplementation;
  }

  public void configureReader(XStream xstream) {
    xstream.useAttributeFor("serviceInterface", String.class);
    xstream.useAttributeFor("serviceImplementation", String.class);
  }
}
