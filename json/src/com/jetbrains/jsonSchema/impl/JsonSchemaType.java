package com.jetbrains.jsonSchema.impl;

/**
 * @author Irina.Chernushina on 7/15/2015.
 */
enum JsonSchemaType {
  _string, _number, _integer, _object, _array, _boolean, _null, _any;

  public String getName() {
    return name().substring(1);
  }
}
