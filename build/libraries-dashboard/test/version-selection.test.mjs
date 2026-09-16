// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

import {deepEqual, equal, ok} from "node:assert/strict"
import {describe, it} from "node:test"
import {classify, compareVersions, detectFork, parseVersion, pickLatest} from "../libraries-dashboard.mjs"

describe("parseVersion", () => {
  it("accepts four numeric segments", () => {
    deepEqual(parseVersion("3.53.4.0").parts, [3, 53, 4, 0])
  })
  it("treats .Final as no suffix", () => {
    const p = parseVersion("4.2.18.Final")
    deepEqual(p.parts, [4, 2, 18, 0])
    equal(p.tokens.length, 0)
    equal(p.prerelease, false)
  })
  it("detects prerelease tokens with digits", () => {
    ok(parseVersion("0.152.0-alpha02").prerelease)
    ok(parseVersion("5.0.0.Alpha1").prerelease)
    ok(parseVersion("2.4.20-RC3").prerelease)
    ok(parseVersion("1.9.20-dev-8162").prerelease)
    ok(parseVersion("2026.1-eap").prerelease)
  })
  it("names the variant family from alpha tokens", () => {
    equal(parseVersion("33.7.1-jre").family, "jre")
    equal(parseVersion("1.18.13-jdk5").family, "jdk5")
    equal(parseVersion("0.7.1-0.6.x-compat").family, "x-compat")
    equal(parseVersion("6.6.1.202309021850-r").family, "r")
    equal(parseVersion("1.5.7-12").family, "")
    equal(parseVersion("4.2.18.Final").family, "")
  })
  it("rejects non-numeric leading segments", () => {
    equal(parseVersion("latest"), null)
    equal(parseVersion(""), null)
  })
})

describe("compareVersions", () => {
  it("compares numeric suffix tokens numerically", () => {
    ok(compareVersions("1.5.7-12", "1.5.7-9") > 0)
    ok(compareVersions("1.5.7-16", "1.5.7-12") > 0)
  })
  it("ranks a release above its prereleases and a build number above the plain release", () => {
    ok(compareVersions("2.4.20", "2.4.20-RC3") > 0)
    ok(compareVersions("2.4.20-RC3", "2.4.20-beta1") > 0)
    ok(compareVersions("2.4.20-RC3", "2.4.20-RC2") > 0)
    ok(compareVersions("1.5.7-12", "1.5.7") > 0)
  })
  it("uses the fourth segment", () => {
    ok(compareVersions("3.53.4.0", "3.53.2.1") > 0)
    equal(compareVersions("4.2.18.Final", "4.2.18"), 0)
  })
})

describe("pickLatest", () => {
  it("keeps four-segment versions", () => {
    const r = pickLatest(["3.36.0", "3.53.2.1", "3.53.4.0"], "3.53.2.1")
    equal(r.latest, "3.53.4.0")
  })
  it("keeps .Final releases and skips 5.0.0.Alpha", () => {
    const r = pickLatest(["4.2.16.Final", "4.2.17.Final", "4.2.18.Final", "5.0.0.Alpha1"], "4.2.18.Final")
    equal(r.latest, "4.2.18.Final")
  })
  it("orders build numbers numerically", () => {
    equal(pickLatest(["1.5.7-9", "1.5.7-12", "1.5.7-16"], "1.5.7-12").latest, "1.5.7-16")
  })
  it("stays inside the variant family", () => {
    equal(pickLatest(["1.17.7", "1.18.13", "1.18.13-jdk5"], "1.17.7").latest, "1.18.13")
    equal(pickLatest(["33.7.1-android", "33.7.1-jre", "33.5.0-jre"], "33.5.0-jre").latest, "33.7.1-jre")
    equal(pickLatest(["0.8.0", "0.8.0-0.6.x-compat", "0.7.1-0.6.x-compat"], "0.7.1-0.6.x-compat").latest, "0.8.0-0.6.x-compat")
  })
  it("reports a note instead of a prerelease when the pin is stable", () => {
    const r = pickLatest(["0.152.0-alpha02"], "0.150.1")
    equal(r.latest, null)
    equal(r.note, "only prereleases: 0.152.0-alpha02")
  })
  it("prefers a stable release even when the pin is a prerelease", () => {
    equal(pickLatest(["2.4.10", "2.4.20-RC3"], "1.9.20-dev-8162").latest, "2.4.10")
    equal(pickLatest(["2.4.20-RC2", "2.4.20-RC3"], "2.4.20-RC2").latest, "2.4.20-RC3")
  })
  it("ignores fork versions in the upstream list", () => {
    equal(pickLatest(["3.0.7", "3.0.0-jetbrains.11"], "3.0.0-jetbrains.11").latest, "3.0.7")
  })
})

describe("classify", () => {
  it("classifies segment differences", () => {
    equal(classify("3.25.5", "4.36.1"), "major")
    equal(classify("2.19.0", "2.22.2"), "minor")
    equal(classify("3.2.1", "3.2.2"), "patch")
    equal(classify("3.53.2.1", "3.53.4.0"), "patch")
    equal(classify("1.5.7-12", "1.5.7-16"), "patch")
    equal(classify("2.3.0", "2.2.2"), "ahead")
    equal(classify("4.2.18.Final", "4.2.18.Final"), "up-to-date")
    equal(classify("1.0", null), "unknown")
  })
})

describe("detectFork", () => {
  it("recognizes JetBrains fork markers", () => {
    ok(detectFork("com.networknt", "3.0.0-jetbrains.11"))
    ok(detectFork("org.jetbrains.intellij.deps.fastutil", "8.5.18-jb1"))
    ok(detectFork("org.jetbrains.intellij.deps", "0.38.0-idea2"))
    ok(detectFork("org.jetbrains.intellij.deps.cucumber", "1.2.6-amn-patched"))
    ok(detectFork("org.sqlite", "3.42.0-jb.1"))
    ok(detectFork("org.jetbrains.intellij.deps", "9.6.1"))
  })
  it("leaves ordinary versions alone", () => {
    equal(detectFork("com.google.guava", "33.5.0-jre"), false)
    equal(detectFork("org.jetbrains", "26.0.2"), false)
    equal(detectFork("org.jetbrains.kotlin", "2.5.0-dev-6810"), false)
  })
})
