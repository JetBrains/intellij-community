// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
import com.google.protobuf.gradle.*

val grpcVersion = "1.57.2"
val grpcKotlinVersion = "0.2.0" // CURRENT_GRPC_KOTLIN_VERSION
val protobufVersion = "3.24.4"
val coroutinesVersion = "1.3.8"

plugins {
    application
    kotlin("jvm") version "1.4.0"
    id("com.google.protobuf") version "0.8.13"
}

repositories {
    mavenLocal()
    jcenter()
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation("javax.annotation:javax.annotation-api:1.2")
    implementation("io.grpc:grpc-protobuf:$grpcVersion")
    implementation("io.grpc:grpc-stub:$grpcVersion")
    implementation("io.grpc:grpc-kotlin-stub:$grpcKotlinVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutinesVersion")
    runtimeOnly("io.grpc:grpc-netty-shaded:$grpcVersion")
}

sourceSets {
    main {
        proto {
            srcDir("$projectDir/proto")
            include("**/*.proto")
        }
        java {
            setSrcDirs(listOf("$projectDir/gen", "$projectDir/src"))
        }
    }
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:$protobufVersion"
    }
    plugins {
        id("grpc") {
            artifact = "io.grpc:protoc-gen-grpc-java:$grpcVersion"
        }
        id("grpckt") {
            artifact = "io.grpc:protoc-gen-grpc-kotlin:$grpcKotlinVersion:jdk7@jar"
        }
    }
    generateProtoTasks {
        ofSourceSet("main").forEach {
          // Skip the intermediate "main/plugin" dirs for generated sources,
          // and place them using the layout more common for the IntelliJ project.
          fun GenerateProtoTask.PluginOptions.copyGeneratedFiles() {
              it.doLast {
                copy {
                  from("${it.outputBaseDir}/${outputSubDir}")
                  into("$projectDir/gen")
                }
              }
            }
            it.builtins {
                remove("java")
                id("java") {
                    copyGeneratedFiles()
                }
            }
            it.plugins {
                id("grpc") {
                    copyGeneratedFiles()
                }
                id("grpckt") {
                    copyGeneratedFiles()
                }
            }
        }
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
}
