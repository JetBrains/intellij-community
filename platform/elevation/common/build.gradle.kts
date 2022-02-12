// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
import com.google.protobuf.gradle.*

val grpcVersion = "1.31.1"
val grpcKotlinVersion = "0.2.0" // CURRENT_GRPC_KOTLIN_VERSION
val protobufVersion = "3.19.4"
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
            srcDir("$projectDir/gen")
        }
    }
}

protobuf {
    // Together with the 'outputSubDir' overridden below in order to skip the intermediate "main/" sourceSet dir,
    // this makes sources files generated using the layout more common for the IntelliJ project.
    generatedFilesBaseDir = "$projectDir/gen"

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
            it.builtins {
                remove("java")
                id("java") {
                    outputSubDir = ".."
                }
            }
            it.plugins {
                id("grpc") {
                    outputSubDir = ".."
                }
                id("grpckt") {
                    outputSubDir = ".."
                }
            }
        }
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
}
