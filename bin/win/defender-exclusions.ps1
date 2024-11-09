<#
   The script adds paths, given as parameters, to the Microsoft Defender folder exclusion list,
   unless they are already excluded.
#>

#Requires -RunAsAdministrator

[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

if ($args.Count -eq 0) {
  Write-Host "usage: $PSCommandPath path [path ...]"
  exit 1
}

try {
  Import-Module Defender

  # expands paths in the exclusion list
  function Expand-Excluded ([string[]] $exclusions) {
    $result = @()
    foreach ($exclusion in $exclusions) {
      try {
        $expanded = [System.Environment]::ExpandEnvironmentVariables($exclusion)
        $resolvedPaths = Resolve-Path -Path $expanded -ErrorAction Stop
        foreach ($resolved in $resolvedPaths) {
          $result += $resolved.ProviderPath.ToString()
        }
      } catch [System.Management.Automation.ItemNotFoundException] {
      } catch [System.Management.Automation.DriveNotFoundException] {
      } catch [System.Management.Automation.WildcardPatternException] {
      }
    }
    return $result
  }

  # returns `$true` when a path is already covered by the exclusion list
  function Test-Excluded ([string] $path, [string[]] $exclusions) {
    foreach ($exclusion in $exclusions) {
      if ([cultureinfo]::InvariantCulture.CompareInfo.IsPrefix($path, $exclusion, @("IgnoreCase"))) {
        return $true
      }
    }
    return $false
  }

  $exclusions = (Get-MpPreference).ExclusionPath
  if (-not $exclusions) {
    $exclusions = @()
  } else {
    $exclusions = Expand-Excluded $exclusions
  }

  foreach ($path in $args) {
    if (-not (Test-Excluded $path $exclusions)) {
      $exclusions += $path
      Write-Host "added: $path"
    } else {
      Write-Host "skipped: $path"
    }
  }

  Set-MpPreference -ExclusionPath $exclusions
} catch {
  Write-Host "$($_.Exception.GetType()): $($_.Exception.Message)"
  Write-Host $_.ScriptStackTrace
  exit 1
}
