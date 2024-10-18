/*
 * Copyright 2001-2007 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.java.generate.test;

import java.util.Date;

/**
 * To be used for testing.
 */
@SuppressWarnings("unused")
public class Pet {

    private String name;
    private Date birthDay;
    private Owner owner;

    public String getName() {
        return name;
    }
    public void setName(String name) {
        this.name = name;
    }
    public Owner getOwner() {
        return owner;
    }
    public Date getBirthDay() {
        return birthDay;
    }

    public void setBirthDay(Date birthDay) {
        this.birthDay = birthDay;
    }

    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Pet pet = (Pet) o;

        if (!birthDay.equals(pet.birthDay)) return false;
        if (!name.equals(pet.name)) return false;
        if (!owner.equals(pet.owner)) return false;

        return true;
    }

    public int hashCode() {
        int result;
        result = name.hashCode();
        result = 31 * result + birthDay.hashCode();
        result = 31 * result + owner.hashCode();
        return result;
    }

    public String toString() {
        return "Pet{" +
                "name='" + name + '\'' +
                ", birthDay=" + birthDay +
                ", owner=" + owner +
                '}';
    }

    public void setOwner(Owner owner) {
        this.owner = owner;
    }
}