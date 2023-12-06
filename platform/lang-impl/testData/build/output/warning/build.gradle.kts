plugins {
  kotlin("jvm") version "1.9.0"
  application
}

group = "org.example"
version = "1.0-SNAPSHOT"
repositories {
  mavenCentral()
}

dependencies {
  testImplementation(kotlin("test"))
}

tasks.test {
  useJUnitPlatform()
}

kotlin {
  jvmToolchain(8)
}
false
application {
  mainClass.set("MainKt")
}