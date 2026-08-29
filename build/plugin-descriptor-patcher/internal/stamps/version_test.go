// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package stamps_test

import (
	"testing"

	"jetbrains.com/plugin-descriptor-patcher/internal/stamps"
)

// The two scalars this binary computes rather than receives. The assembly computes them from the same two functions,
// so a disagreement here moves the `<version>` and the `<idea-version>` of every plugin - which is what the byte gate
// of `./build/dev-dist.cmd descriptors` would report as 0 identical.

func TestThePluginBuildNumber(t *testing.T) {
	for name, it := range map[string]struct {
		buildNumber string
		want        string
	}{
		"a snapshot takes the fixed number and a nightly zero": {"263.SNAPSHOT", "263.99999999.0"},
		"a three-segment snapshot needs no zero":               {"263.100.SNAPSHOT", "263.100.99999999"},
		"a released number is unchanged":                       {"263.100.5", "263.100.5"},
		"a two-segment number takes a zero":                    {"263.100", "263.100.0"},
	} {
		t.Run(name, func(t *testing.T) {
			got, err := stamps.PluginBuildNumber(it.buildNumber)
			if err != nil {
				t.Fatal(err)
			}
			if got != it.want {
				t.Errorf("got %s, want %s", got, it.want)
			}
		})
	}
}

// The platform checks the result against Semantic Versioning and fails when it does not match
// (`SnapshotBuildNumber.kt`). A build number that reaches a jar unchecked is the failure this prevents.
func TestABuildNumberThatIsNotSemanticFails(t *testing.T) {
	for _, buildNumber := range []string{"263.x.1", "abc", "263..1"} {
		if _, err := stamps.PluginBuildNumber(buildNumber); err == nil {
			t.Errorf("%s must be refused", buildNumber)
		}
	}
}

func TestTheCompatiblePlatformVersionRange(t *testing.T) {
	for name, it := range map[string]struct {
		compatibleBuildRange stamps.CompatibleBuildRange
		buildNumber          string
		wantSince            string
		wantUntil            string
	}{
		"exact pins both ends": {stamps.RangeExact, "263.100.5", "263.100.5", "263.100.5"},
		"restricted to the same release keeps every segment but the last": {
			stamps.RangeRestrictedToSameRelease, "263.100.5", "263.100", "263.100.*",
		},
		"restricted over two segments keeps both": {
			stamps.RangeRestrictedToSameRelease, "263.100", "263.100", "263.100.*",
		},
		"newer with the same baseline stars the baseline": {
			stamps.RangeNewerWithSameBaseline, "263.100.5", "263.100", "263.*",
		},
		"a build number of another shape pins both ends": {
			stamps.RangeNewerWithSameBaseline, "263.SNAPSHOT", "263.SNAPSHOT", "263.SNAPSHOT",
		},
	} {
		t.Run(name, func(t *testing.T) {
			since, until := stamps.CompatiblePlatformVersionRange(it.compatibleBuildRange, it.buildNumber)
			if since != it.wantSince || until != it.wantUntil {
				t.Errorf("got (%s, %s), want (%s, %s)", since, until, it.wantSince, it.wantUntil)
			}
		})
	}
}
