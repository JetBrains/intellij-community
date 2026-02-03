package helpers

import (
	"os"
	"repair/logger"
	"runtime"
	"testing"
)

func TestMain(m *testing.M) {
	code := m.Run()
	if code == 0 {
		logger.RemoveLogFile()
	}
	os.Exit(code)
}

var tests = []struct {
	name                  string
	wantPossible_binaries map[string][]string
	wantErr               bool
}{
	{name: "in /Applications folder",
		wantPossible_binaries: map[string][]string{"darwin": []string{"/Applications/IntelliJ IDEA.app"}, "windows": []string{"C:\\Program Files\\JetBrains\\IntelliJ IDEA 2021.1.2"}},
		wantErr:               false,
	},
}

func TestFindPossibleIdeInstallations(t *testing.T) {

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			gotPossible_binaries, err := FindInstalledIdePackages()
			if (err != nil) != tt.wantErr {
				t.Errorf("FindInstalledIdePackages() error = %v, wantErr %v", err, tt.wantErr)
				return
			}
			if !sliceContainsAll(gotPossible_binaries, tt.wantPossible_binaries[runtime.GOOS]) {
				t.Errorf("FindInstalledIdePackages() gotPossible_binaries = %v, want %v", gotPossible_binaries, tt.wantPossible_binaries)
			}
		})
	}
}

func sliceContainsAll(allValues []string, mandatoryValues []string) (containsAll bool) {
	for _, mandatoryElement := range mandatoryValues {
		for _, element := range allValues {
			if mandatoryElement == element {
				containsAll = true
				break
			}
		}
		if containsAll == false {
			return false
		}
	}
	return true
}
